# Australian Yacht Racing Elapsed Time Database

A database and analysis tool for elapsed times from Australian yacht racing, with statistical
analysis and graphical presentation. Developed by [Mort Bay Consulting Pty. Ltd.](https://mortbay.com)

---

## What This Project Does

This project collects publicly available race results from Australian sailing clubs, stores the
elapsed times of competing boats, and computes a **Historical Performance Factor (HPF)** for
each boat from that history.

The HPF is the back-calculated handicap a boat would have needed, averaged across its racing
history, to have been equal-time with a hypothetical 1.000 reference boat. It is a measure of
past performance, anchored where possible to independently verifiable measurement-based handicaps
(IRC, ORC, AMS).

Results and HPF values are presented graphically via a web interface, and are also available
as open data via a REST JSON API for anyone who wishes to build on them.

---

## What This Project Is Not

**This is not a handicap system.**

The HPF values produced by this project are historical performance measures, not allocated
handicaps. They are intended to:

- Inform the **initial allocation of a handicap** when a boat joins a series for the first time
- Help a handicapper **evaluate whether an existing handicap is reasonable** in light of a boat's
  broader racing history across multiple clubs
- Provide a **cross-club performance reference** for boats that race at multiple venues

The project is explicitly **not** intended to replace the way individual clubs age or adjust
handicaps between races. Day-to-day handicap management — responding to a boat's recent
results within a series — remains the responsibility of each club's handicapper using their
normal processes and systems (e.g. PHS).

Any use of HPF values as direct handicap allocations is at the discretion of the handicapper
and club concerned. The values are one input among many, not a definitive answer.

---

## Data

### Raw Data

Race result data is collected from publicly available sources:

- **SailSys** — via the public SailSys API (`api.sailsys.com.au`)
- **TopYacht** — via publicly accessible club result pages

Data collected includes boat entry details (sail number, name, design, club), elapsed times,
and measurement-based handicap certificates (IRC, ORC, AMS) where published. Performance
handicap (PHS) values assigned by clubs are explicitly excluded.

This project makes no copyright claim over the raw race result data. It is reproduced as a
matter of public record. See `LICENSE.md` for full provenance details.

### Derived Data

HPF values, reference factors, and other analytical outputs computed by this project are
published under the **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**
licence. You are free to use and build on this data provided you attribute the source and
publish any derivative datasets under the same licence.

ORC certificate data is sourced from the ORC public data feed and used in accordance with
ORC's open data policy. IRC values are sourced exclusively from publicly published race
results — the restricted RORC/YCF TCC database is not used.

---

## How It Works

The analysis is anchored on **measurement-based handicaps** (IRC, ORC, AMS) held by boats in
the fleet. These serve as reference points — boats with certificates are treated as known
quantities against which the performance of all other boats is calibrated.

For boats without certificates, reference quality is propagated through the race network: a
boat that has raced against certificated boats acquires an implied reference, which in turn
anchors boats it has raced against, and so on. The strength of each boat's reference
degrades with distance from a direct certificate anchor.

An iterative optimisation then finds, for each boat, the HPF that best fits its elapsed times
across all races in scope, subject to the pull of its reference anchor. The strength of that
pull is proportional to the quality of the boat's reference — boats with strong certificates
stay close to their certificate value; boats with only indirect references float more freely
toward what the race data suggests.

Full technical details are in the project documentation:

- `project_description.md` — overview and motivation
- `processing_pipeline.md` — step-by-step pipeline description
- `object_model_architecture.md` — Java object model and layering principles
- `id_strategy.md` — how entities are identified and disambiguated
- `data_sources_and_formats.md` — SailSys, TopYacht, ORC and IRC data formats
- `presentation_layer.md` — REST API and browser-based charting frontend

---

## Technology

- **Backend:** Java, Spring Boot
- **Database:** Relational (PostgreSQL or similar)
- **Frontend:** Plain JavaScript, Plotly.js for interactive charts
- **Deployment:** Cloud Run or Fly.io (scale-to-zero)

---

## Licence

- **Source code:** Apache License 2.0
- **Derived data (HPF values etc.):** CC BY-SA 4.0
- **Raw race results:** No claim — public record

See `LICENSE.md` for full details.

---

## Status

Early development. Data ingestion and optimisation layers are being built first;
the web frontend follows once real HPF outputs are available to present.

Contributions and issue reports are welcome.
