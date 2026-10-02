# Feature: Browser and Platform Detection

## Decision

**Owner module**: `analytics` (or `clicks`) | **Event-coupled**: Y (`ClickLoggedEvent` — enrich with User-Agent metadata)

Parses the `User-Agent` header on each redirect to identify browser family and operating system, storing the result for analytics. Aggregated stats available via a read-model endpoint.

**Traps**: raw `User-Agent` stored as-is is a privacy liability; parsing with regexes is fragile — use a dedicated library; rollup queries on millions of clicks without read-model materialisation will be slow.

**Technology hints**: `ua-parser-java`; listen to `ClickLoggedEvent` in the `analytics` module; `@Query` with JPA for aggregation; anonymise IP before storage.

---

- **Weight:** 8

- **Event-coupled:** Y

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] …
- [ ] …

### Scale evidence

Fill this section with the evidence of the scalability of the feature.

### Qualities

Fill **Assessed** with the grade you claim; **How to test** must falsify that number if it failed.

| Quality | Assessed | How to test |
| --- | --- | --- |
| **Correctness** | 0 \| 5 | Acceptance tests for this feature |
| **Scalability** | 0–3.5 | Scale evidence above. Not `ModularityTests`. |
| **Engineering** | 0 \| 0.75 \| 1.5 | Hard gates: AI disclosure + `./gradlew check`. Then ADR + `ModularityTests`: both → **1.5**, one → **0.75**. |

**Indicative total:** _ / 10

### AI disclosure

- **Tools / skills:** …
- **Used for:** …
- **Human-reviewed:** …
- Or: **No AI assistance** was used for this feature.
