# Presentation Layer

## Architecture

The presentation layer is a **browser-based client** consuming a **RESTful JSON API** served by the Spring Boot backend. There is no server-side HTML rendering — the backend serves data only; the frontend handles all display and interaction.

This architecture serves two goals:
1. A rich, interactive user experience with client-side charting
2. An openly accessible JSON API that third parties can consume directly to build their own tools on top of the project's derived data

---

## Backend: REST API

- Served by the Spring Boot application alongside the data and optimisation layers
- Returns JSON; all derived data (HPF values, reference factors, residuals) is available via API endpoints
- The API is the mechanism by which the CC BY-SA 4.0 derived data is made openly available

Key query shapes the API must support:
- **Series view** — for a given series, return per-boat HPF estimates across all races in the series
- **Boat comparison** — for two or more specified boats, return all races in which they have competed and their HPF estimates in each
- **Design comparison** — for two or more specified designs, return aggregate HPF by design across all races where they have competed

---

## Frontend: JavaScript Charting

- A static single-page application (HTML + JS), served as static assets from the Spring Boot app or a CDN
- Charts rendered client-side using a JavaScript charting library (Plotly.js is the leading candidate — it produces interactive, publication-quality charts and is open source)
- No React or build pipeline complexity — plain JS or a lightweight framework is preferred given the limited UI scope

### Intended Chart Types
- **Time series / scatter** — HPF estimates per boat per race within a series, showing consistency and drift over time
- **Box plots or violin plots** — HPF distribution per boat or per design across a season
- **Comparison overlays** — two or more boats or designs on the same axes

---

## Key Constraint

The presentation layer is deliberately **last in the build sequence**. The data ingestion, reference network, and HPF optimisation layers are built first. The REST API surface is defined once real optimisation outputs are available to inspect. The frontend is built against that real API.

This avoids designing charts against hypothetical data shapes.
