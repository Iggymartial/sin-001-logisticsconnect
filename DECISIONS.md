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

## Stage 2: synchronous REST integration

**Decision: delay-stage-service tracks a stage PER HUB (a map), not
one global value.** Different from the companion HealthSafe project's
alert-level-service, which tracked a single system-wide status. The
integration contract here is explicit that each hub has its own
independent delay stage (`GET /delay-stage/{hubId}`), which makes
sense domain-wise too - a weather shutdown at one hub shouldn't
imply anything about a hub on the other side of the country.

**Decision: a hub with no delay stage ever explicitly set defaults to
stage 0, rather than 404.** transit-service needs SOME stage value for
any hub it's asked to compute an ETA for. Treating "never reported" as
"no known delay" is more useful than forcing every single hub to be
explicitly initialised before an ETA can ever be calculated - and it's
a reasonable real-world default: absence of a reported delay is a
better assumption than an arbitrary one.

**Decision: transit-service fails loudly (503) if delay-stage-service
is unreachable, rather than silently assuming stage 0 and returning an
ETA anyway.** This was a genuine judgement call - a "fail open" design
would keep the ETA endpoint always answering something. I chose to
fail closed instead: a falsely optimistic ETA (assuming no delay when
the real answer is simply unknown) could actively mislead someone
relying on it, which is worse than an honest "I can't tell you right
now". Matches the same reasoning already applied to an equivalent
situation in the companion HealthSafe project, kept consistent
deliberately.

**Decision: the ETA formula produces a WIDENING window, not just a
later single point, as the delay stage rises.** The README describes
"estimated arrival windows" (plural, range-based), not a single
timestamp. Modelled earliest and latest hours growing at different
rates (2h/stage vs 5h/stage) so that a bigger disruption is reflected
as both later AND less certain - verified this stays monotonically
increasing and the window never narrows across the full 0-8 range
before trusting it.

**Decision: HubClient reuses the sealed-interface pattern
(Found/NotFound/Unavailable) from the companion HealthSafe project's
WardClient.** Same problem, same solution - the compiler forces every
caller to handle all three outcomes explicitly, which is exactly the
signal this stage is meant to demonstrate: not assuming the happy path
when calling a downstream service.

## Stage 3: MQ decoupling (package-status-topic)

**Decision: reused DelayStage (adding a timestamp field) as both the
REST response shape and the MQ event payload**, rather than a separate
event type. Same reasoning as the companion HealthSafe project: the
two are identical in shape, so a second type would be pure duplication
with no benefit yet.

**Decision: the delay-stage-service map now stores the full DelayStage
record, not a bare int.** This was needed once GET had to return a
real timestamp - for a hub that's genuinely had its stage set, that
timestamp should reflect when it was actually set, not be fabricated
fresh on every read.

**Decision: transit-service's failure handling deliberately CHANGES
between Stage 2 and Stage 3, and this is intentional, not an
inconsistency.** In Stage 2, an unreachable delay-stage-service meant
"my last-known information could be actively stale or wrong" - failing
loudly (503) was the right call there, to avoid a falsely optimistic
ETA. In Stage 3, "no event received yet for this hub" means something
different: it genuinely means no delay has ever been reported for that
hub, which is exactly the same default delay-stage-service itself
already applies. Defaulting to stage 0 here isn't papering over
missing information the way it would have been in Stage 2 - it's the
correct interpretation of what "no message yet" actually means in a
push-based system.

**Decision: kept the subscription non-durable, same as the companion
project's topic.** If transit-service is offline when a stage change
is published, that update is simply missed, not queued for later
delivery - accepted as the correct tradeoff for a topic used for
broadcast status, not something requiring guaranteed delivery.

**Correction I caught while writing this:** first wrote the subscriber
parsing the incoming message as a generic JSON tree instead of the
shared DelayStage type, for no real reason - the event's fields match
DelayStage exactly, so typed deserialization is simpler and more
consistent with how the equivalent subscriber works in the companion
project. Fixed it before finishing the stage rather than leaving an
inconsistent, needlessly complicated version in.

**Real incident during testing:** delayStage stayed at 0 even after a
successful POST, on the first attempt. Checked docker-compose.yml and
both services' MqConfig.java directly against each other first - they
agreed exactly at that point (both port 61616), so it wasn't a
mismatch between the project's own files. The actual cause turned out
to be environment-specific: Windows was refusing to bind port 61616 at
all (`ports are not available... access forbidden by its access
permissions`), a known Windows/Docker Desktop issue where the OS
reserves parts of the port range (commonly related to Hyper-V/WSL's
dynamic port allocation) out from under Docker.

Fixed by remapping the broker to a different host port -
`docker-compose.yml` now maps `61660:61616` instead of `61616:61616` -
and updating `BROKER_URL` in BOTH `MqConfig.java` files (
delay-stage-service and transit-service) from `tcp://localhost:61616`
to `tcp://localhost:61660` to match. This is a genuine, permanent
environment fix, not a one-off glitch that resolved itself - worth
remembering if this project is ever run on a different machine where
61616 isn't blocked, since the two files now need to agree with
whatever port is actually in use, not necessarily the default.

Once both sides were consistently pointed at the correct port, the
full flow worked immediately: POSTing a new stage for H-503 published
to package-status-topic, and the very next GET to transit-service's
/eta endpoint reflected it correctly - delayStage: 6, etaEarliestHours:
36, etaLatestHours: 54, exactly matching the formula verified in Stage
2. This closes out Stage 3 - the required core of this second project
is now fully built and genuinely proven working against real, live
services.
