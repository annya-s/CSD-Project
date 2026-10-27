# Machine-learning research and planning pack

Prepared 17 October 2026 for **outdoor, soil-grown crops across regions**. This pack provides research and implementation plans. It does not add crops to the app, change the database, train a model or claim measured predictive accuracy.

## The four requested deliverables

| Request | Document | Contents |
| --- | --- | --- |
| More plants and optimal conditions | [Additional crop profiles](01-crop-catalog-expansion.md) | 20 additions, bringing the research catalogue to 30; sourced temperature, pH and annual rainfall; limits for other metrics |
| Historical conditions and growth outcomes | [Dataset and website register](02-historical-data-sources.md) | Ranked field datasets, aggregate outcomes, weather/soil enrichment, metric compatibility, access/license gaps and ingestion plan |
| Confidence level plan | [Prediction and recommendation confidence](03-confidence-plan.md) | Targets, independent labels, probability calibration, confidence gates, abstention, validation and farmer-facing wording |
| Dynamic farmer advice | [Dynamic advice plan](04-dynamic-advice-plan.md) | Rules, context, changing advice, priorities, conflict handling, feedback, implementation phases and verification |

## Main recommendation

**Updated scope, 22 October 2026:** start with the [weather-only wheat implementation roadmap](05-weather-only-implementation-roadmap.md). It identifies the four required dataset files, exact columns and joins, real biomass regression target, source-compatible weather inputs, synthetic scenario generation, and the Python/Java/UI files to create or edit. Pest prediction is excluded from this first version. This update takes precedence over the earlier broad recommendation below.

Start with a **maize growth/yield benchmark** using USDA Bushland and then G2F for broader environments. Assess the Bulgarian wheat data for a separate, limited plant-status experiment. In parallel, collect dated field observations and management actions from app users. Public yield data can teach performance relationships, but cannot establish whether a specific crop is healthy today.

Keep three product claims separate: environmental suitability, estimated observed stress, and future growth/yield. A recommendation also needs its own evidence and applicability checks; a confident risk prediction does not prove a particular intervention will work.

## Phase 0: Current-code baseline

The code was inspected because the older project documentation no longer describes the automatic weather path accurately.

| Metric | Current provider field | Meaning / current assessment |
| --- | --- | --- |
| Temperature | `temperature_2m` | Hourly °C, aggregated into a full-day forecast mean; compared with broad crop reference |
| Sunshine | `sunshine_duration` | Daily seconds, displayed/compared as hours; repeated in hourly records but taken only once |
| Soil moisture | `soil_moisture_3_to_9cm` | Modelled shallow-layer VWC in m³/m³; displayed as percent by volume; not assessed |
| Vapour-pressure deficit | `vapour_pressure_deficit` | Hourly kPa, daily mean; not assessed; currently lost when snapshot is reloaded from storage |
| Relative humidity | Not requested | Legacy `humidityMeanPercent` field exists but is null in the automatic path |
| pH, rain, plant growth/health observations | Not collected by this crop-weather path | Proposed future inputs/labels, not available features |

### Verified implementation facts

- [EnvironmentClient](../../backend/src/main/java/com/csd/farm/environment/EnvironmentClient.java) calls `/v1/forecast` using the **server's timezone**. [CropWeatherService](../../backend/src/main/java/com/csd/farm/crop/CropWeatherService.java) selects **today**, not yesterday.
- [CropForecast](../../backend/src/main/java/com/csd/farm/crop/CropForecast.java) aggregates the full day's forecast, including hours later today. Full hourly coverage does not make it a completed historical observation. The aggregation checks completeness and basic bounds, but the response contract does not carry provider unit metadata for validation.
- [CropWeatherRepository](../../backend/src/main/java/com/csd/farm/crop/CropWeatherRepository.java) overwrites fields on `farm.crop_entry`; there is no append-only weather history. It does not persist VPD. The fallback can display an old verified-provider snapshot with a warning, but has no model-specific maximum-age gate.
- [CropHealthService](../../backend/src/main/java/com/csd/farm/crop/CropHealthService.java) compares temperature and sunshine, emits rule-based advice, and leaves VPD/water unassessed. `healthScore` in [CropResponses](../../backend/src/main/java/com/csd/farm/crop/CropResponses.java) is always supplied as null by [CropService](../../backend/src/main/java/com/csd/farm/crop/CropService.java). There is no ML inference or confidence implementation.
- Fetching depends on requests from the pages. There is no background evidence collection for farms whose pages are closed.
- Identity remains `(farmer_id, crop_type, planted_at)`; there is no stable field/planting UUID. Farmer IDs in current code are `Long`/database `BIGINT`.
- [Current configuration](../../backend/src/main/resources/application.properties) disables Flyway. Coordinate the explicit [Supabase setup](../../backend/sql/supabase-setup.sql), existing-database update process and [demo schema](../../backend/src/main/resources/db/demo/schema.sql). Legacy migration files alone are not the active production schema process.

The older [integration guide](../crop-health-integration.md) describes yesterday, location-resolved timezones, RH and methods/classes absent from the current code. Use this inspected baseline for planning; do not copy those outdated method names. Existing crop-reference research remains useful with its stated scope limitations.

## Existing integration points versus proposed additions

| Existing pattern to reuse | Proposed addition |
| --- | --- |
| `EnvironmentClient.fetchMeteoData(double, double)` and `EnvironmentService.getAllReadings(double, double)` | Historical collection and explicit forecast vintages, field timezone, unit metadata and source version |
| `CropHealthService.assess(CropType, DailyWeather)` | Keep reference comparisons; introduce separately named prediction/confidence/advice services |
| Ownership lookup in `CropService.details` before assembling the response | Apply the same authorization to observation, prediction, feedback and history routes |
| `GET /api/crops/{cropType}?plantedAt=...` and `GET /api/dashboard` | Add response fields compatibly; no new ML route is assumed to exist |
| Backend-served `static/crop-details.js` and `dashboard.js` | Display probability, confidence, evidence time and structured actions; the Vue dashboard is not the current implementation |

The manual weather POST is disabled while automatic fetching is enabled. Do not use that endpoint as an unplanned production field-sensor ingestion API.

## Recommended delivery sequence

1. **Agree the target and data contract — ML + backend.** Name the outcome and assessment time; resolve forecast/observation, timezone, soil-depth and VPD-storage gaps.
2. **Audit and ingest a small paired trial — ML.** Record rights, provenance, dictionary and joins; inspect real samples before committing to a model architecture.
3. **Collect durable app evidence — backend + agronomic reviewer.** Add histories, plot/crop context, scouting labels and management events; preserve ownership and existing records.
4. **Build and evaluate a baseline — ML.** Hold out independent farms/regions/seasons; calibrate and evaluate using deployment-compatible inputs.
5. **Deliver reviewed dynamic advice — backend + frontend + agronomic reviewer.** Implement issue state, prerequisites, conflicting-action checks and follow-up.
6. **Run a limited pilot — whole team.** Use the confidence release gates, document supported contexts and expand only when evidence supports it.

Each detailed plan specifies verification and anti-pattern guards. No runtime tests were required for this documentation-only change; model and implementation checks described in the plans remain future work.

## Research boundaries

Numerical crop references were checked against primary pages. Dataset research used provider landing pages, author papers and selected data dictionaries; it did not audit full files or live Supabase. The source register identifies inaccessible pages, unverified licenses and unresolved units explicitly. These are actionable acquisition checks, not assumptions to hide in the model.
