# ID Strategy

## Principles

- IDs are **never derived from source system proprietary identifiers** (e.g. SailSys internal IDs). Source system IDs may be used transiently during ingestion to interpret incoming data, but are not stored as primary keys. They may be stored in alias/mapping tables for ingestion purposes only.
- IDs are **human readable** where practical — a developer or administrator should be able to identify an entity from its ID without a database lookup.
- IDs are **stable once assigned** — they do not change if canonical names or sail numbers are later corrected.
- Where natural keys are sufficiently clean and stable, they are used directly. Where source data is dirty or ambiguous, a generated surrogate ID is used with aliases recording the raw source data.

---

## Clubs

**Key:** The club's current website domain name (e.g. `manlysc.com.au`, `mbyc.com.au`).

**Rationale:** Domain names are globally unique by definition, human readable, self-documenting, and entirely independent of any source system. They are stable — clubs rarely change domains.

**Aliasing:** If a club has changed domain names historically, the canonical key is the *current* domain. Former domains are stored as aliases.

**Source system mapping:** A small hand-maintained configuration file maps each source system's club identifier (e.g. SailSys club ID) to the canonical domain-name key. This file is built incrementally as new sources are ingested. There are few enough clubs in Australian sailing that this is manageable.

**Known collisions:** Club names are not unique nationally — there are at least two clubs called "Manly Yacht Club" (Manly NSW: `manlysc.com.au`; Wynnum QLD: `mbyc.com.au`). The domain-name key resolves this unambiguously.

---

## Boats

**Key format:** `{normalisedSailNumber}-{firstWordOfName}-{hex}`

Examples: `aus1234-raging-3f9a`, `3721-azzuro-12bc`

**Construction rules:**

- `normalisedSailNumber`: lowercase, strip all non-alphanumeric characters, collapse spaces. E.g. `AUS-1234` → `aus1234`, `aus 1234` → `aus1234`.
- `firstWordOfName`: first whitespace-delimited word of the canonical name, lowercased, non-alphanumeric stripped. E.g. `Raging Bull` → `raging`, `TenSixty` → `tensixty`, `Komatsu Azzuro` → `komatsu`.
- `hex`: a short random hex suffix (4 digits) used only when the `sailNumber-name` combination would otherwise collide with an existing ID. Attempt assignment without the suffix first; append and retry only on collision.

**Rationale:** Sail numbers are mostly unique but not guaranteed so; the name fragment adds disambiguation. The hex suffix handles the rare genuine collision. The result is compact, memorable, and source-system-independent.

**Aliasing:** Each Boat has a list of Alias records capturing the raw source data:

```
Alias {
    sourceSystem,      // e.g. "sailsys", "topyacht"
    sourceClubId,      // the source system's club identifier (transient use only)
    rawSailNumber,     // exactly as it appeared in the source
    rawName            // exactly as it appeared in the source
}
```

Ingestion matching uses `normalisedSailNumber` + name similarity + club context to resolve an incoming record to an existing Boat, or to create a new one. A new Alias is added for each distinct raw representation encountered.

---

## Designs

**Key:** Normalised design name (lowercase, non-alphanumeric stripped, e.g. `j24`, `farr40`, `sydneyhobart34`).

**Rationale:** Design names are controlled vocabulary — they originate from manufacturers and class associations and are stable. Collisions are extremely unlikely.

**Maker linkage:** A Design references one or more Makers. Most designs have a single maker, but some (e.g. J/24) have been built by multiple manufacturers.

**Aliasing:** Designs may be known by more than one name, typically because the same hull was badged differently by different manufacturers or at different points in time. Examples: the Mumm 30 and Farr 30 are the same design; similarly the Mumm 36 and Farr 36. One name is chosen as canonical (typically the more commonly used current name) and the others are stored as aliases. Ingestion normalisation maps all known aliases to the canonical design key. The alias list is hand-maintained as new equivalences are discovered during data ingestion.

---

## Makers

**Key:** Normalised maker name (lowercase, non-alphanumeric stripped).

---

## Seasons

**Key:** A short label representing the season span, e.g. `2024-25` for the Australian season running spring 2024 to winter 2025. Single-calendar-year seasons use `2024`.

---

## Series

**Key:** `{clubDomain}/{season}/{normalisedSeriesName}`

Example: `manlysc.com.au/2024-25/wednesday-twilight`

**Normalisation:** Series names are lowercased, non-alphanumeric characters replaced with hyphens, multiple hyphens collapsed.

**Source system mapping:** As with clubs, a hand-maintained mapping file resolves source system series identifiers to canonical series keys. Series names within a single club/season are unlikely to collide even with minor variations, so this mapping is expected to be straightforward.

**Catch-all series:** Each club may have a pseudo-series named `events` for races that do not belong to any real series (e.g. standalone offshore races). Its key follows the same pattern: `manlysc.com.au/2024-25/events`. This series is flagged `isCatchAll: true` and is excluded from series-level aggregate analysis. Every Race belongs to at least one Series; no special null-series handling is required.

---

## Races

**Key:** Surrogate generated ID — `{clubDomain}-{isoDate}-{hex}`

Example: `manlysc.com.au-2024-11-06-4a1f`

**Rationale:** A race's identity is not cleanly derivable from any series it belongs to, because a race can belong to multiple series. The organising club and date are stable natural attributes; the hex suffix handles the case of multiple races on the same day by the same club (not uncommon for clubs running multiple divisions or back-to-back races).

**Named vs numbered races:** Whether a race is identified within its series by a number (Race 7) or a name (Flinders Race) is stored as a race attribute, not reflected in the primary key. The primary key is always the club+date+hex form.

---

## Race Entries

**Key:** Composite of `{raceId}+{boatId}`. A boat enters a given race at most once (it may be in one division only, and holds one set of classifications for that race).

---

## Measurement Certificates

**Key:** Surrogate generated ID — `{boatId}-{type}-{year}-{hex}`

Example: `aus1234-raging-3f9a-irc-2024-001`

**Rationale:** A boat may hold multiple certificates of the same type in the same year (different configurations). The hex/sequence suffix disambiguates.

---

## Summary Table

| Entity | Key Type | Key Pattern |
|---|---|---|
| Club | Natural | website domain name |
| Maker | Natural | normalised name |
| Design | Natural | normalised name |
| Season | Natural | `2024-25` label |
| Series | Natural composite | `clubDomain/season/normalisedName` |
| Boat | Generated slug | `sailnum-firstname-hex` |
| Race | Generated slug | `clubDomain-date-hex` |
| RaceEntry | Composite | `raceId+boatId` |
| MeasurementCertificate | Generated slug | `boatId-type-year-hex` |
