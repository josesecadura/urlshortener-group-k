# Project Agreement — Group K

**Due:** 2 October 2026.

> Rules are in [`Group Project`](https://moodle.unizar.es/add/course/section.php?id=1702745).  
> Feature cards are in [`docs/features/`](docs/features/).  
> This file is the cover sheet: one fact, one place.  
> Personal names, feature ownership, git identity, and the integration owner are in **`TEAM.md`** — git-ignored, must not be committed, but must be included in the zip submission.

## Features

The three seed features (create short URL, redirect and click log, link stats) are provided. Do not list them here.

Each row below is a **grown** feature: a card copied from the Feature Catalogue, or a new card in that same shape, plus a chosen weight. Personal names and ownership belong in `TEAM.md`, not here.

| Feature card | Owner module | Weight | Event-coupled |
| --- | --- | --- | --- |
| [docs/features/0004-async-click-ingestion-via-outbox-broker.md](docs/features/0004-async-click-ingestion-via-outbox-broker.md) | clicks | 21 | Y |
| [docs/features/0005-browser-and-platform-detection.md](docs/features/0005-browser-and-platform-detection.md) | analytics | 8 | Y |
| [docs/features/0006-redirect-cache-with-cross-replica-invalidation.md](docs/features/0006-redirect-cache-with-cross-replica-invalidation.md) | links | 21 | Y |V
| [docs/features/0007-qr-code-generation.md](docs/features/0007-qr-code-generation.md) | links | 8 | N |
| [docs/features/0008-url-management-deactivation.md](docs/features/0008-url-management-deactivation.md) | links | 13 | N |
| [docs/features/0009-url-expiration.md](docs/features/0009-url-expiration.md) | links | 13 | N |


## Budget

| Rule | Value | Status |
| --- | --- | --- |
| Σ weights | **= 84** | OK |
| Grown features | **≥ 4** (one per member) | OK |
| Event-coupled | **≥ 2** | OK |

## Commitment

We commit to:

- Implementing the feature cards listed above and keeping them up to date.
- Each member being able to explain and demo the feature they own at the oral defence.
- Maintaining CI green and `docker compose up` working throughout the project.
- Disclosing AI assistance on each feature card before grading.

By pushing this file we confirm the above commitment.
