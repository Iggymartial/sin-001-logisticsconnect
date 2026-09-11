package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Cleans the deliberately messy hubs-global.csv into a normalised,
 * de-duplicated list of HubRecord. Kept separate from
 * IngestionServiceApp so this logic - the actual point of this
 * service - is testable in isolation.
 *
 * DUPLICATE-DETECTION STRATEGY - different from a simpler "same ID,
 * different casing" approach, and deliberately so: this CSV's
 * duplicates have entirely DIFFERENT hub IDs describing the same real
 * place (e.g. H-500, H-504, H-510, H-515 are four different IDs all
 * describing "Johannesburg Central"). Matching on ID would therefore
 * miss every duplicate in this file. Instead, duplicates are matched
 * on normalised sorting_center name ALONE, not (province,
 * sorting_center) together - this is what correctly catches the
 * H-508 case, where the province field is blank entirely. A sorting
 * center name in this dataset is specific enough on its own to be a
 * reliable identity signal, and no two different provinces in this
 * file share a sorting center name.
 *
 * CONFLICT-VS-CLEAN-MERGE DISTINCTION for the active flag: if
 * duplicate rows for the same hub all agree on active (or only one of
 * them has a valid value), the merge is clean. If they genuinely
 * DISAGREE (multiple valid but contradictory values), the result is
 * left null and flagged rather than resolved by majority vote or
 * "last one wins" - unlike a simple bad-data case (one unparseable
 * value vs one good one), this is two EQUALLY VALID but contradictory
 * signals, and guessing wrong on an operational active/inactive flag
 * has real consequences. A stricter pipeline might reject the whole
 * merge for manual review instead; this one keeps the pipeline
 * running but refuses to silently pick a side.
 */
public class HubCsvCleaner {

    // Only the spelling variant actually present in this file
    // (KwaZulu-Natal, three different ways) - not a generic dictionary.
    private static final Map<String, String> PROVINCE_SYNONYMS = Map.of(
            "kwa-zulu natal", "KwaZulu-Natal",
            "kwazulu natal", "KwaZulu-Natal",
            "kwazulu-natal", "KwaZulu-Natal"
    );

    private static final List<String> MISSING_PLACEHOLDERS = List.of(
            "", "n/a", "na", "tbd", "unknown", "-", "nan"
    );

    private static final List<String> TRUE_VALUES = List.of("y", "yes", "true", "1");
    private static final List<String> FALSE_VALUES = List.of("n", "no", "false", "0");

    public List<HubRecord> clean(InputStream csvInput) throws IOException, CsvValidationException {
        List<HubRecord> rawCleaned = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(csvInput, StandardCharsets.UTF_8))) {
            reader.readNext(); // discard header row

            String[] row;
            while ((row = reader.readNext()) != null) {
                HubRecord record = cleanRow(row);
                if (record != null) {
                    rawCleaned.add(record);
                }
            }
        }

        return mergeDuplicates(rawCleaned);
    }

    private HubRecord cleanRow(String[] row) {
        if (row.length < 4) {
            return null; // malformed row - skipped, not crashed on
        }

        List<String> notes = new ArrayList<>();

        String hubId = collapseSpaces(row[0]).toUpperCase();
        if (hubId.isBlank()) {
            return null; // no usable identifier
        }

        String province = normaliseProvince(row[1], notes);
        String sortingCenter = normaliseSortingCenter(row[2], notes);
        Boolean active = normaliseActive(row[3], notes);

        return new HubRecord(hubId, province, sortingCenter, active, notes);
    }

    private String normaliseProvince(String raw, List<String> notes) {
        String trimmed = collapseSpaces(raw);
        if (isPlaceholder(trimmed)) {
            notes.add("province was missing/placeholder ('" + raw.trim() + "')");
            return null;
        }

        String synonym = PROVINCE_SYNONYMS.get(trimmed.toLowerCase());
        if (synonym != null) {
            notes.add("province normalised from spelling variant '" + trimmed + "' to '" + synonym + "'");
            return synonym;
        }

        return titleCase(trimmed);
    }

    private String normaliseSortingCenter(String raw, List<String> notes) {
        String trimmed = collapseSpaces(raw);
        if (isPlaceholder(trimmed)) {
            notes.add("sortingCenter was missing/placeholder ('" + raw.trim() + "')");
            return null;
        }
        return titleCase(trimmed);
    }

    private Boolean normaliseActive(String raw, List<String> notes) {
        String value = collapseSpaces(raw).toLowerCase();

        if (TRUE_VALUES.contains(value)) {
            return true;
        }
        if (FALSE_VALUES.contains(value)) {
            return false;
        }

        notes.add("active was unrecognised/placeholder ('" + raw.trim() + "')");
        return null;
    }

    /**
     * Groups by normalised sortingCenter and merges each group into one
     * record. See the class-level comment for the reasoning behind
     * matching on sortingCenter alone, and behind treating active
     * conflicts differently from active gaps.
     */
    private List<HubRecord> mergeDuplicates(List<HubRecord> records) {
        Map<String, List<HubRecord>> bySortingCenter = new LinkedHashMap<>();

        for (HubRecord record : records) {
            String key = record.sortingCenter() == null ? "" : record.sortingCenter().toLowerCase();
            bySortingCenter.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }

        List<HubRecord> result = new ArrayList<>();

        for (List<HubRecord> group : bySortingCenter.values()) {
            if (group.size() == 1) {
                result.add(group.get(0));
                continue;
            }
            result.add(mergeGroup(group));
        }

        return result;
    }

    private HubRecord mergeGroup(List<HubRecord> group) {
        HubRecord first = group.get(0);

        String province = group.stream()
                .map(HubRecord::province)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        Set<Boolean> distinctActiveValues = new LinkedHashSet<>();
        for (HubRecord r : group) {
            if (r.active() != null) {
                distinctActiveValues.add(r.active());
            }
        }

        List<String> notes = new ArrayList<>();
        List<String> mergedIds = group.stream().map(HubRecord::hubId).toList();
        notes.add("merged " + group.size() + " duplicate rows for this hub: " + mergedIds);

        Boolean active;
        if (distinctActiveValues.size() > 1) {
            active = null;
            StringBuilder conflictDetail = new StringBuilder();
            for (HubRecord r : group) {
                if (conflictDetail.length() > 0) conflictDetail.append(", ");
                conflictDetail.append(r.hubId()).append("=").append(r.active());
            }
            notes.add("CONFLICT: active values disagree across duplicates (" + conflictDetail
                    + ") - flagged, not guessed");
        } else if (distinctActiveValues.size() == 1) {
            active = distinctActiveValues.iterator().next();
        } else {
            active = null;
        }

        return new HubRecord(first.hubId(), province, first.sortingCenter(), active, notes);
    }

    private boolean isPlaceholder(String value) {
        return MISSING_PLACEHOLDERS.contains(value.toLowerCase());
    }

    private String collapseSpaces(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    private String titleCase(String value) {
        String[] words = value.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
