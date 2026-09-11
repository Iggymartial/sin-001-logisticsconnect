package co.wethinkcode.logisticsconnect;

import java.util.List;

/**
 * The cleaned, de-duplicated shape of one hub record, ready to be
 * served over REST and consumed by hub-service.
 *
 * active is nullable Boolean, not boolean: null specifically means
 * "we could not determine a trustworthy value" - either the raw value
 * was an unrecognised placeholder (unknown, N/A), or - more
 * significantly - multiple duplicate rows for the same real hub gave
 * genuinely CONFLICTING active values, and guessing which one is
 * correct isn't something this cleaner does silently. See
 * HubCsvCleaner for the reasoning.
 */
public record HubRecord(
        String hubId,
        String province,
        String sortingCenter,
        Boolean active,
        List<String> notes
) {}
