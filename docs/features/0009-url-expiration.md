# Feature: URL Expiration

## Decision

**Owner module**: `links` | **Event-coupled**: N (self-contained in links; event coupling only needed at Level 3+ when another module must react to expiry)

Users optionally set an expiration date/time at creation. Accesses after expiry return 410 Gone via Problem Detail. Background sweep deletes or deactivates expired rows.

**Traps**: the sweep runs N times on N replicas with a naive `@Scheduled` — requires a DB-level lock (ShedLock) or leader election to ensure single execution. Claiming Level 3 (weight 13) requires a new event of your own. Adding `expiresAt` to `ShortUrlCreatedEvent` reuses the seed event and does not count.

**Technology hints**: `@Scheduled` + ShedLock; `@Future` Bean Validation; soft-delete pattern.

---

- **Weight:** 13

- **Event-coupled:** N

## Later (after the 2 October agreement)

- **ADR:** none | docs/adr/NNNN-….md *(by when a choice locks a module, the broker, or a library)*

### Acceptance criteria

- [ ] POST /api/link con una fecha de caducidad antigua responde 400 (BadRequest error)
- [ ] POST /api/link con una fecha de caducidad posterior a la fecha actual responde 201, incluye la cabecera Location y el JSON contiene hash.
- [ ] GET /$hash con hash siendo el correspondiente a una url caducada (que todavia no se ha eliminado de la BD por el scheduler) responde 410 Gone y no existe cabecera Location
- [ ] GET /$hash con hash siendo el correspondiente a una url valida (no caducada)responde 307 Temporary Redirect
- [ ] GET /$hash con hash siendo el correspondiente a una url inexistente devuelve 404 Not Found
- [ ] GET /api/stats/$hash con hash siendo el correspondiente a una url valida (no caducada) devuelve 200 OK con LinkStatsResponse(hash, totalClicks)
- [ ] GET /api/stats/$hash con hash siendo el correspondiente a una url inexistente devuelve 404 Not Found

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
