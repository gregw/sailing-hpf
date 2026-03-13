# Object Model Architecture

## Previous Implementation

There is a previous Java implementation of this project in the repository. It was a deliberate **quick-and-dirty exploratory prototype** — written to understand the domain, not as a production design. It should be read to understand domain knowledge (entities, relationships, edge cases, field names) but **must not be used as an architectural template**. Specific design problems are listed below.

---

## Known Problems in the Previous Implementation

### 1. Static Global Registries
Classes like `Boat` use a static `ArrayList` as a global singleton registry (e.g. `__byId`). This makes it impossible to run multiple optimisation scopes simultaneously, breaks unit testing, and creates hidden shared state. **Do not replicate this pattern.**

### 2. Raw and Derived Data Mixed in the Same Class
Mutable derived fields (computed handicaps, race counts, running averages) sit alongside immutable raw fields in the same class. For example, `Boat` holds both the raw `_name`/`_sailNumber` (final) and mutable `_spinnakerHC`/`_racedSpinnaker` (derived). These have fundamentally different lifecycles and must be separated.

### 3. Back-References Creating Circular Graphs
`Boat` holds a `List<Entry>` — a direct back-reference from parent to child. This creates circular object graphs that cause problems for serialisation (Jackson infinite recursion), testing, and reasoning about object ownership. **Navigation from parent to children must go through index objects, not fields on the parent.**

### 4. Domain Logic in the Wrong Place
The `add()` method on `Boat` contains MYC-specific logic for tracking the latest handicap date. Club-specific concerns must not live in a generic domain object.

### 5. Analysis Logic in Domain Objects
`addRaced()` computes a running average handicap inline on `Boat`. Analysis and optimisation logic belongs in dedicated service classes, not in the entities being analysed.

### 6. Save/Restore Pattern
`saveHC()` / `restoreHC()` exist to support optimisation iterations. This is a symptom of mutable derived state living on the raw object. In the new design, optimisation state lives in dedicated mutable working objects; the raw layer is never mutated.

---

## New Architecture Principles

### Layer Overview

The architecture has four layers with a strict one-way dependency:

```
Optimised derived  →  Deterministic derived  →  Raw
Index              →  Raw
Raw                →  (nothing in this project)
```

---

### Layer 1: Raw — Records, Always Persisted, Immutable

The raw layer captures data exactly as ingested from source systems. It is persisted to the database. Once stored, raw data is never mutated.

- Use **Java records** for all raw layer entities — immutability, compact syntax, and value semantics are all desirable.
- Raw records hold **no back-references** and **no derived fields**.
- Raw records know nothing about any other layer.

Key raw records: `Boat`, `Race`, `RaceEntry`, `MeasurementCertificate`, `Club`, `Design`, `Series`, `Season`.

---

### Layer 2: Deterministic Derived — Records, Persisted, Invalidated on Raw Change

The deterministic derived layer is computed from the raw layer by **pure functions with no tuning parameters**. Given the same raw data, it always produces the same result. It is persisted as a materialised view — recomputed only when the raw data it depends on changes.

- Use **Java records** — these values are immutable once computed.
- Cache invalidation is straightforward: track which raw records each derived record depends on; invalidate when those raw records change.
- Recomputation is triggered by new race ingestion, new certificate arrival, or design alias updates — not by user configuration.

Key deterministic derived records:
- `BoatReference` — the reference factor and weight for a boat, derived from its measurement certificates and race co-participation (pipeline Steps 8–12)
- `DesignReference` — the aggregated reference factor for a design, derived from its fleet of certificated boats
- `RaceDispersion` — weighted IQR and dispersion metrics for a race, derived purely from elapsed times and reference factors

These are the **anchors** for the optimisation layer. The optimisation layer treats them as fixed inputs, not variables.

---

### Layer 3: Optimised Derived — Mutable Classes, Never Persisted

The optimised derived layer is produced by the HPF optimisation (pipeline Steps 13–19). It is **non-deterministic** in the sense that its output depends on configuration (scope, λ, convergence threshold, number of outer iterations) and the iterative optimisation process. It is never persisted — always recomputed on demand with whatever configuration the user requests.

- Use **ordinary mutable classes** for working objects updated during alternating least squares iterations.
- Use **records** for final output snapshots once optimisation converges — these are read-only results handed to the presentation layer.
- Working objects hold references to raw records and deterministic derived records as fixed inputs; they never mutate those inputs.

Key optimised derived types:
- `EntryWeight` (mutable, working) — the weight assigned to a race entry during iterations
- `RaceContext` (mutable, working) — the current T estimate for a race during iterations
- `BoatHpfEstimate` (mutable, working) — the current HPF estimate for a boat during iterations
- `BoatHpf` (record, final output) — the converged HPF, confidence, and per-race residuals for a boat

---

### Layer 4: Index — Derived, Cheap, Optionally Persisted

Navigation that would require back-references on raw records (e.g. "all races for a boat") is handled by **index objects** rather than collection fields on parent entities. Indexes are derived deterministically from raw data and can be rebuilt at any time at negligible cost for the expected dataset size (~600 boats, ~50 races/year).

```java
// Rather than: boat.getEntries()  ← do not do this
// Use:         entryIndex.entriesByBoatId(boat.id())
```

Index objects may optionally be persisted to accelerate startup, but correctness never depends on the persisted index — it can always be recomputed from the raw layer.

---

### Persistence Boundary Summary

| Layer | Persisted? | Condition |
|---|---|---|
| Raw | Always | Source of truth |
| Deterministic derived | Yes | Invalidated when upstream raw data changes |
| Index | Optional | Performance only; always recomputable |
| Optimised derived | Never | Recomputed on demand per configuration |

If any object needs to be serialised for an API response, a custom Jackson serialiser writes only the ID of any referenced raw record — it does not expand the full raw record inline.

---

### Service Layer

Analysis and optimisation logic lives in dedicated service classes, not in entity objects:

- `ReferenceNetworkBuilder` — computes `BoatReference` and `DesignReference` records (deterministic derived layer, Steps 8–12)
- `HpfOptimiser` — runs the alternating least squares optimisation (optimised derived layer, Steps 13–17)
- Importers (`SailSysImporter`, `TopYachtImporter`) — translate raw source data into raw layer records

Entity records and classes hold data. Services hold behaviour.
