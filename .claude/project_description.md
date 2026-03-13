# Project Description: Australian Yacht Racing Elapsed Time Database & Analysis

## Overview

The goal is to build an online database of elapsed times for all boats racing in Australia in recent years, with statistical analysis and graphical presentation tools. The primary motivating use case is Manly Yacht Club (MYC, NSW), which has a small fleet, meaning useful analysis requires drawing on data from many clubs across Australia.

---

## Data Collection

### SailSys
- JSON data downloads are available from a single SailSys website.
- A SailSys importer will be built to consume these JSON feeds.

### TopYacht
- TopYacht results are published across many individual club websites.
- A manually curated list of club result page URLs will be maintained as input.
- A web scraper will be built to extract results from these HTML pages.
- Discovery of new TopYacht result pages is a project activity (searching the web for club result pages to add to the list).

### Data Included
- Boat entry details (sail number, name, design, club, owner/representative)
- Elapsed times per race
- Measurement-based handicap certificates (IRC, ORC/ORCi/ORCclub, AMS) where published

### Data Excluded
- PHS handicap numbers assigned to boats
- Computed PHS race results (corrected times, positions derived from PHS)
- Note: elapsed times from PHS races are valid and will be used

---

## Boat Identity & Disambiguation

Boat identity is complex and must be handled carefully:
- Identified by sail number and name, neither of which is guaranteed unique.
- Name variations must be handled (e.g. "TenSixty" vs "1060", "Azzuro" vs "Komatsu Azzuro").
- Sail number typographic variations must be handled (e.g. "AUS-1234" vs "aus 1234").
- Disambiguation by club, design, and owner information where available.
- A boat may be associated with one or more clubs.

---

## Standard Candles

Statistical analysis is anchored on "standard candles" — boats for which performance can be independently estimated from measurement-based handicaps.

### Candle Quality Tiers
1. **Best candle**: A boat with an IRC/ORC/AMS handicap *used in the race being analysed*, with a history of racing against other measurement-handicapped boats, ideally of the same design.
2. **Good candle**: A boat with an IRC/ORC/AMS certificate used for the race.
3. **OK candle**: A boat holding an IRC/ORC/AMS certificate, even if racing under PHS in a given race (elapsed time used, PHS number ignored).
4. **Weak candle**: A boat of a design for which many other boats hold IRC/ORC/AMS certificates, allowing the design's performance envelope to be characterised even if this specific boat lacks a certificate.

### Candle Propagation
- Races without direct candles may still include boats that have raced elsewhere against candles.
- Boats of candle designs further extend the network.
- The goal is to build a web of relationships connecting all boats — however indirectly — back to the measurement-handicap anchor.

### Single Number Handicap
- Each measurement certificate will be reduced to a single handicap number for analysis purposes (e.g. IRC TCC, ORC GPH, AMS value).
- The candle quality score will be stored alongside the handicap value.

---

## Handicap Estimation & Optimisation

### Back-Calculated Handicap
- For any race, the "back-calculated handicap" for a boat is the handicap it would have needed for all boats to share the same corrected time.
- This requires estimating the expected elapsed time for a hypothetical **1.000 handicap boat** in each race.

### Estimating the 1.000 Boat Reference Time
- Derived from the elapsed times of competing boats weighted by their candle quality and their estimated relationship to candles.
- Used to normalise race durations so that races of different lengths and conditions can be compared within a series.

### Self-Referential Circularity
- There is an acknowledged circularity: the 1.000 reference time is derived from boat elapsed times, which are then evaluated against that reference.
- Mitigation strategy: seed the optimisation with known candle values (deterministic anchor points), then use a **simulated annealing-style optimisation** to find the best-fit allocated handicaps for all boats consistent with the observed results.
- Preference for deterministic values wherever possible (e.g. a measurement certificate value is treated as a fixed anchor, not a variable).

### Asymmetry Principle
- It is easy for a boat to sail slower than its potential; it is essentially impossible to sail faster.
- This asymmetry should be incorporated into the statistical model: unexpectedly fast performances are treated with more suspicion than unexpectedly slow ones, except where weather/tide gates can explain them.
- In small fleets, an apparently outstanding result is more likely to reflect poor performance by others than a genuine exceptional performance.

---

## Race & Result Weighting

### Race-Level Weighting
- Some races will have extraordinary results making them unsuitable for analysis (e.g. severe weather splits, gear failure affecting many boats).
- Algorithms will be developed to detect and down-weight (rather than simply exclude) such races.
- Weighting at the race level is preferred over excluding individual boat results where possible.

### Boat-Level Weighting
- Individual boats may have poor performances that should be down-weighted.
- Since it is difficult to distinguish bad sailing from bad luck, caution will be applied before excluding individual boat results.
- Boats that are consistently variable (different crew, unforced errors) will naturally accumulate lower candle quality scores.

### Weather/Tide Gates
- Races where one group of boats experiences significantly different conditions will be flagged and potentially excluded or specially handled.

---

## Cross-Race Normalisation

To compare races within a series:
- The fleet composition changes race to race.
- Race durations vary.
- The 1.000 reference time is used to normalise all elapsed times to a common basis.
- This allows meaningful comparison of boat performance across races despite changing fleets and conditions.

---

## Website & Visualisation

A website will present the data and analysis graphically. Example views include:

- **Series view**: All races in a series plotted with back-calculated handicaps for each boat in each race.
- **Design comparison**: Select two or more designs and plot their relative performance across all races where they have competed.
- **Boat comparison**: Select two or more specific boats and plot all races in which they have competed against each other.
- Other views to be defined as the project evolves.

---

## Technology

- **Developer background**: Strong Java, competent JavaScript, historical C/C++. Open to new languages and infrastructure.
- **Tech stack**: Not yet determined; to be selected based on suitability.
- **Database**: To be determined (likely a relational database given the structured nature of the data).
- **Scraping**: Custom scraper for TopYacht HTML pages, driven by a curated list of URLs.
- **Data ingestion**: JSON importer for SailSys data.

---

## Scope

- Primary focus: Manly Yacht Club (MYC), NSW.
- Broader data collection: all available SailSys clubs in Australia, plus TopYacht clubs in NSW and/or Australia (via curated URL list).
- The project does not specifically target any single software vendor's clubs; mixing SailSys and TopYacht sources is deliberate.
