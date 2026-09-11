# Design Decisions Log

A running record of the "why" behind key choices in this project, kept
as I build - not written after the fact.

## Stage 1: Ingestion

**Observation: this CSV's duplicate problem is structurally different
from a simpler "same ID, different casing" case.** The README's own
hint confirms it: `H-500`, `H-504`, `H-510`, and `H-515` are four
entirely different hub IDs all describing the same real place
("Johannesburg Central"). Matching duplicates by normalised ID
wouldn't work here at all - the IDs themselves are genuinely
different. I had to key duplicate detection off the place name
(sorting center) instead.

**Decision: match duplicates on normalised sortingCenter alone, not
(province, sortingCenter) together.** Reasoning: one duplicate pair in
this file (`H-502` / `H-508`, both "Pretoria North") has a completely
blank province on one side. If I'd required province to also match,
this pair would never be recognised as duplicates at all. A sorting
center name in this specific dataset is distinctive enough on its own
to be a reliable identity signal, and no two different provinces here
happen to share a sorting center name.

**Decision: province spelling variants get normalised BEFORE duplicate
matching, not after.** Three rows describing "Durban Harbour" spell
KwaZulu-Natal three different ways (`KwaZulu-Natal`, `Kwa-Zulu Natal`,
`KwaZulu Natal`). This doesn't actually affect matching (since I match
on sortingCenter alone), but it does matter for the final merged
record having one consistent, correct province name rather than
whichever spelling happened to be on the first row encountered.

**Decision: when duplicate rows genuinely DISAGREE on the active flag,
leave it null and flag the conflict - never resolve it by majority
vote or "last one wins".** This is a deliberately different rule from
how I handled a superficially similar situation in a previous project,
and the difference is deliberate: there, one duplicate had a valid
number and the other had unparseable text - an easy call, keep the
usable one. Here, multiple rows have EQUALLY VALID, directly
CONTRADICTORY active values (e.g. three rows say a hub is active, one
says it's inactive). That's not "one is bad data" - it's two genuine,
conflicting signals, and guessing wrong on an operational
active/inactive flag has real consequences (routing packages to a hub
that's actually closed, or the reverse). Flagging it and moving on
keeps the pipeline running without pretending to know something the
data doesn't actually tell me.

**Verified the full cleaning and merge logic against the real CSV
before writing any Java**, the same way as my other project: I
reimplemented the algorithm in a language I could actually run in the
environment I was working in, ran it against the real 18-row file, and
hand-checked every one of the four duplicate groups against what I'd
worked out by reading the raw data myself first. All four groups
resolved exactly as expected: two clean merges (all rows agreed), two
flagged conflicts (rows genuinely disagreed), and the
blank-province edge case correctly matched anyway. 18 raw rows reduced
to 10 trustworthy hub records.
