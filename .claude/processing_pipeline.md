# Processing Pipeline

## Overview

This document describes the full processing pipeline for the Australian Yacht Racing Elapsed Time Database. The pipeline is divided into two phases: **Data Preparation and Reference Network Construction** (steps 1–12) and **HPF Calculation** (steps 13–19).

---

## Phase 1: Data Preparation and Reference Network Construction

### Step 1: Fetch ORC Australian Certificate Data

If not done within the configured refresh interval, fetch the ORC Australian certificate list from:

```
https://data.orc.org/public/WPub.dll?action=activecerts&CountryId=AUS
```

For each certificate in the list, fetch the full certificate detail page using the `dxtID` field. This provides:

- Boat name and sail number
- ORC certificate information (GPH, VPP data, certificate type, expiry)
- Design name via the `Class` field — authoritative, controlled vocabulary
- Hull file reference via `dxtName` — identifies sister ships and design variants

For each boat, create or update a Boat instance using the canonical ID strategy. For each design name encountered, create or update a Design instance. Store the certificate as a MeasurementCertificate record linked to the boat.

---

### Step 2: Fetch TopYacht Boat Register

If not done within the configured refresh interval, fetch the TopYacht boat register from:

```
https://www.topyacht.com.au/mt/boat_list.php
```

For each boat in the register, fetch the individual boat detail page (`mt_pub.php?boid=`). This provides:

- Boat name and sail number
- Design name
- IRC and ORC ratings where held

For each boat, create or update a Boat instance. Store the TopYacht `boid` as an alias for ingestion mapping purposes. Create any new Design instances encountered. Design names from the TopYacht register are cleaner than SailSys but less authoritative than ORC — use ORC `Class` as the canonical design name where both exist.

---

### Step 3: Refresh SailSys Boat Records

For all boats whose details originated from SailSys and have not been updated within the configured refresh interval, fetch the boat JSON from:

```
https://api.sailsys.com.au/api/v1/boats/{id}
```

Update the Boat instance with any changes. The SailSys boat endpoint is authoritative for SailSys-sourced handicap values and physical measurements. Note that `make` and `model` fields can bleed together in SailSys — apply normalisation rules and prefer ORC or TopYacht design names where available.

---

### Step 4: Scan TopYacht Results Pages for New or Updated Races

Scan the manually curated list of TopYacht club result page URLs for new or updated race results. For each result page not yet ingested, or whose content has changed since last ingestion, download the results.

For each result page, extract:
- Race metadata (series name, club, race number, date, start time, handicap system)
- Per-boat entries: boat name, sail number, skipper, home club, elapsed time, allocated handicap used for scoring

Elapsed times from PHS races are valid and will be stored. PHS handicap values and PHS-derived corrected times are excluded. The allocated handicap from IRC, ORC or AMS races is stored as the handicap used for scoring in that race entry.

---

### Step 5: Scan SailSys Races for New or Updated Races

Scan SailSys race records starting from a configurable start index (set to avoid re-scanning the full history on each run). For each race, fetch:

```
https://api.sailsys.com.au/api/v1/races/{id}/resultsentrants/display
```

A race is considered complete when `lastProcessedTime` transitions from null to non-null. Store `lastProcessedTime` per race and re-ingest if it has changed since last fetch. For each completed race, extract the same fields as Step 4. The `handicapCreatedFrom` field in `calculations[]` is the handicap actually used for scoring — use this in preference to `currentHandicaps[].value`.

---

### Step 6: Create Boat Instances for Unknown Boats in SailSys Races

For any boat appearing in a SailSys race result that cannot be matched to an existing Boat instance (via normalised sail number, name similarity and club context), fetch the boat detail:

```
https://api.sailsys.com.au/api/v1/boats/{id}
```

Create a new Boat instance using the canonical ID strategy. Add an Alias record capturing the raw sail number and name as they appeared in the source. Apply the same process for unknown boats appearing in TopYacht results, using the `boid` from the boat name link to fetch the TopYacht boat detail page.

---

### Step 7: Create Series and Match to Known Clubs

As races are ingested, create Series instances and match them to known Club instances using the club information embedded in the race records.

If a race references a club that cannot be matched to any known Club instance, store the race in a pending state and emit a prompt so that the manually maintained club URL list can be updated. Pending races are not processed further until their club is resolved. No data is silently dropped.

---

### Step 8: Build the Reference Factor Network from Measurement Certificates

Scan all Boat instances for measurement certificates (IRC, ORC, AMS). For each certificate, derive a single reference handicap number:

- **IRC**: use TCC directly
- **ORC**: convert GPH to TCF using `TCF = 600 / GPH`
- **AMS**: use the AMS value directly

Assign each boat a reference factor and an initial reference factor weight based on the quality of its certificate(s):

| Certificate basis | Weight |
|---|---|
| IRC or ORC certificate used in the race being analysed | 1.0 |
| IRC or ORC certificate held, used for the race | 0.9 |
| IRC or ORC certificate held, but racing under PHS | 0.8 |
| No certificate, but design has many certificated boats | 0.7 |

Where a boat holds certificates of more than one type, combine them as a weighted aggregate. Record the contributing certificates and their weights alongside the reference factor.

---

### Step 9: Aggregate Reference Factors to Design Level

For all designs, find all Boat instances of that design that have a reference factor. Aggregate these into a design-level reference factor using a weighted mean in log space:

```
log(referenceFactor_design) = Σ( w_boat × log(referenceFactor_boat) ) / Σ(w_boat)
```

The design-level reference factor weight is set to 0.7 (reflecting that it is inferred from sister ships rather than a certificate held by this specific boat), scaled by the number and quality of contributing boats.

---

### Step 10: Propagate Reference Factors via Race Co-participation

For all boats that do not yet have a reference factor, find all races in which they have competed against one or more boats that do have a reference factor. For each such race, estimate the unweighted boat's implied reference factor from the ratio of elapsed times relative to the reference boats in that race.

Aggregate these implied values across all qualifying races using a weighted mean in log space, where the weight of each race's contribution is proportional to the total reference factor weight of the reference boats present in that race.

Assign the resulting value as the boat's reference factor with a weight reflecting the indirectness of the derivation.

---

### Step 11: Apply Design-Level Reference Factor as Fallback

For all boats that still have no reference factor after Step 10, assign the design-level reference factor computed in Step 9 (if one exists for their design), with the design-level weight.

---

### Step 12: Iterate Until Convergence

Repeat Steps 9, 10 and 11 in sequence. Each iteration may produce new design-level reference factors (as more boats acquire individual reference factors) and new boat-level reference factors (as more races become usable for propagation).

Continue iterating until no new reference factors are assigned in a full pass. If more than 10 iterations complete without full convergence, halt with an error. This indicates a subgraph that is entirely disconnected from the measurement certificate network — resolution requires manually adding a synthetic reference anchor for the affected design or club.

At the end of Step 12, every boat and design in the database will have a reference factor and an associated weight. Boats with high-quality certificates will have weights near 1.0; boats whose reference factor was derived through many hops of propagation will have lower weights. This weight is carried forward into all subsequent HPF calculations.

---

## Phase 2: HPF Calculation

### Step 13: Compute Initial Reference Time per Race

For each race, for each boat entry, compute a **factor-corrected elapsed time**:

```
correctedTime = elapsedTime / referenceFactor
```

Take the **weighted median** of these values across all boats in the race, weighted by each boat's reference factor weight. This is the initial **reference time** T₀ for the race — the estimated elapsed time a 1.000 HPF boat would have taken to complete the course.

Use weighted median rather than mean because elapsed time distributions are right-skewed — slow outliers due to gear failure, bad wind holes or tactical errors are common and should not distort the baseline.

Record the **weighted IQR** of the corrected times as a first-pass measure of race dispersion. Races with high IQR relative to T₀ are candidates for down-weighting in subsequent steps.

---

### Step 14: Compute Initial Per-Boat HPF Estimates

For each race entry, compute the boat's initial HPF estimate for that race:

```
HPF_race = T₀ / elapsedTime
```

This is the handicap the boat would have needed to equal the median corrected time in that race. Aggregate across all races for a boat using a **weighted mean in log space**:

```
log(HPF_boat) = Σ( w_r × log(HPF_race) ) / Σ(w_r)
```

Where w_r is the race's aggregate reference weight — the sum of reference factor weights of all boats in that race. Working in log space ensures that being 10% fast and 10% slow are treated symmetrically, which is the correct prior before asymmetric weighting is applied in Step 15.

---

### Step 15: Assign Initial Race and Entry Weights

For each race, compute a **race weight** based on:

- Total reference factor weight of all boats in the race — more and better-anchored boats give higher weight
- IQR / T₀ ratio — higher dispersion gives lower weight, reflecting a less reliable race

For each boat entry within a race, compute an **entry weight** based on:

- The boat's reference factor weight
- How far the boat's corrected time deviates from T₀ — entries more than k×IQR from the median are down-weighted (k ≈ 2.0, to be tuned)
- **Asymmetry principle**: entries where the boat was surprisingly fast relative to T₀ are down-weighted more aggressively than entries where the boat was slow. It is essentially impossible for a boat to genuinely sail faster than its potential; an apparently outstanding result in a small fleet is more likely to reflect poor performance by others or a weather/tide gate than genuine exceptional performance.

---

### Step 16: Alternating Least Squares Optimisation (Log Space)

The two unknowns are:

- **HPF** for each boat — one value per boat, stable across all races in the optimisation scope
- **T** for each race — one reference time per race

The objective is to minimise across all entries in scope:

```
Σ w_entry × ( log(elapsedTime) + log(HPF_boat) - log(T_race) )²
```

plus a **regularisation term** pulling each boat's HPF toward its reference factor:

```
+ λ × referenceFactor_weight × ( log(HPF_boat) - log(referenceFactor) )²
```

λ is a global tuning parameter controlling the overall strength of the anchor. The per-boat pull is modulated by each boat's individual reference factor weight — boats with high weight stay close to their reference factor value; boats with low weight float more freely toward what the race data suggests.

**Alternating steps:**

- **A — Fix HPF, solve for T per race:** Each T_race has a closed-form weighted mean solution:
  ```
  log(T_race) = Σ( w_entry × (log(elapsedTime) + log(HPF_boat)) ) / Σ(w_entry)
  ```
- **B — Fix T, solve for HPF per boat:** Each HPF_boat has a closed-form solution combining the race evidence with the regularisation pull:
  ```
  log(HPF_boat) = [ Σ( w_entry × (log(T_race) - log(elapsedTime)) ) + λ × referenceFactor_weight × log(referenceFactor) ]
                  / [ Σ(w_entry) + λ × referenceFactor_weight ]
  ```

Iterate A and B until convergence — defined as the maximum change in any HPF value across an iteration falling below a threshold (e.g. 0.0001).

---

### Step 17: Recompute Weights and Iterate

After convergence of Step 16, recompute entry weights using the residuals from the fitted model:

```
residual = log(elapsedTime) + log(HPF_boat) - log(T_race)
```

A positive residual means the boat was slower than expected; a negative residual means it was faster. Re-apply the asymmetry principle: large positive residuals are down-weighted moderately; large negative residuals are down-weighted more aggressively.

Flag races where a large fraction of entries have high absolute residuals — this signals a weather/tide gate or other race-level anomaly. In this case, down-weight the entire race rather than individual entries.

Return to Step 16 with the updated weights and re-run to convergence. Repeat until weights stabilise across outer iterations (typically 3–5 outer iterations are sufficient).

---

### Step 18: Scope of Optimisation

The optimisation can be run at several scopes, from narrowest to broadest:

- **Single race** — useful for inspection and debugging; T is fixed, only HPF values float
- **Single series** — the natural unit for initial handicap allocation; T floats per race, HPF per boat is shared across all races in the series
- **Single club** — all series at a club within a season; boats appearing in multiple series receive a consistent HPF across them
- **Full fleet** — all clubs and series in the database; maximises the data available for each boat, with the regularisation term preventing drift between disconnected sub-graphs

The regularisation term is essential for full-fleet scope: without it, the solution is only identified up to a global scale factor and sub-graphs with no cross-club boats can drift arbitrarily.

---

### Step 19: Output

For each boat in the optimisation scope, emit:

- **HPF** — the Historical Performance Factor: the back-calculated handicap the boat would have needed to be equal-time with a 1.000 reference boat, averaged across its racing history in scope
- **HPF confidence** — derived from the total weighted race count and consistency of results across races
- **HPF vs reference factor delta** — how far the optimised HPF drifted from the reference factor anchor; large deltas on boats with high reference factor weight warrant investigation
- **Per-race residuals** — the deviation of each race result from the fitted model, for visualisation of boat consistency over time

HPF is explicitly a **historical performance measure**, not a future handicap allocation. It is intended to inform the allocation of initial handicaps at season start or when a new boat joins a series, and is not a replacement for the way individual clubs age or adjust handicaps between races.
