# Connect Java to the Python model

**Status: proposed integration, not connected.** Python already accepts prediction requests. Java does not call it, and the current database does not contain the history it needs. No backend changes or database migrations are applied by this guide.

The result is experimental winter-wheat dry aboveground **biomass in g/m²**, not a health percentage. Training geography is in Bulgaria; it is not validated for Singapore, other crops or hydroponics. Even a correctly connected request can be `UNSUPPORTED` because of location, planting age or feature ranges. Matching the weather source alone does not make predictions valid for arbitrary farms.

## 1. Try the existing service first

Use PowerShell in the repository root. Follow [README setup](README.md#run-locally-powershell-from-repository-root) if `ml/.venv` or trained artifacts are missing. Start Python in one terminal and leave it running:

```powershell
ml/.venv/Scripts/python.exe -m csd_weather.service --host 127.0.0.1 --port 8001
```

For this loopback-only demonstration, leave `ML_API_TOKEN` unset. If it is already configured, both requests below need `-Headers @{ Authorization = "Bearer $env:ML_API_TOKEN" }` with the same token as the server. In a second terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8001/ready
Invoke-RestMethod http://127.0.0.1:8001/predict -Method Post -ContentType 'application/json' -InFile ml/examples/request.json
```

The archived example demonstrates the contract; it is not a current farm reading. `/ready` checks model availability. `/predict` returns JSON and saves a local copy under `ml/predictions/`. Neither endpoint accesses the database or a weather provider. Python computes the nine features and loads the saved model; it does not retrain for each request.

## 2. Fill the data gap before using real records

The request shape is defined in [contracts.py](src/csd_weather/contracts.py), validated in [features.py](src/csd_weather/features.py), and demonstrated completely in [request.json](examples/request.json). Send:

- `crop: winter_wheat`, `growing_system: outdoor_soil`, actual `sowing_date`, `prediction_date`, latitude and longitude. Optional `farmer_id` and timezone-aware `planted_at` must come from the trusted backend.
- `history` with `schema_version: weather-history-v1`, trained `profile: ifs-soil-0-7-v1`, IANA timezone, coordinates, and `response` containing hourly/daily arrays and exact unit dictionaries.
- Every hourly instant in the **previous 14 complete local days**, excluding prediction day: Unix timestamps, temperature (°C), RH (%), VPD (kPa), **0–7 cm** soil moisture (m³/m³), and sunshine duration (seconds in each hour).
- Fourteen daily records: local-midnight Unix timestamps, sunshine totals and astronomical daylight duration, both in seconds. Daily sunshine must equal the sum of hourly sunshine. Daylight is not the same as sunshine. Account for daylight-saving days rather than always assuming 336 hours.

Temperature, VPD, soil moisture and sunshine each produce 7-day and 14-day means, plus planting age: nine features. RH and daylight are required auxiliary inputs, even though they are not direct model features. Do not fill missing history with today's values or invent RH/VPD/sunshine.

Current Java behavior explains why this cannot work immediately:

| Existing file (under `backend/src/main/java/com/csd/farm/`) | Current behavior / required future work |
|---|---|
| `environment/EnvironmentClient.java` | Fetches `/v1/forecast`: hourly temperature, **3–9 cm** soil moisture and VPD; daily sunshine. Does not request RH, hourly sunshine, daylight or the required historical window/profile. Add a compatible history collection path before inference. |
| `environment/OpenMeteoResponse.java`, `EnvironmentReading.java`, `EnvironmentService.java` | Parse the current forecast fields. A history path needs its own complete timestamps, units and profile metadata. |
| `crop/CropWeatherService.java`, `CropForecast.java` | Aggregate today's full-day forecast, including future hours. That cannot stand in for 14 completed historical days. |
| `crop/CropWeatherRepository.java` | Updates one latest snapshot on `farm.crop_entry`; it does not append history. VPD is not persisted. A humidity column exists, but the automatic client does not fetch RH. |
| `crop/CropEntry.java` | Holds crop type, planting timestamp and coordinates. Add explicit verified winter-wheat variant, outdoor-soil context, agronomic sowing date and field timezone; do not assume generic `WHEAT` or the record timestamp proves these. |

The application should collect and retain compatible history in its database; Python should consume that saved history. **3–9 cm cannot be renamed or scaled into 0–7 cm.** Changing soil depth/profile would require a separately trained and validated model.

Keep the existing 3–9 cm forecast for its separate display. For ML, the proposed Java collector should use the matching [Open-Meteo Historical Weather API](https://open-meteo.com/en/docs/historical-weather-api), `https://archive-api.open-meteo.com/v1/archive`, with explicit `models=ecmwf_ifs`, which provides the 0–7 cm layer. Do not substitute the default best-match source. Mirror the existing offline `weather.py` functions `request_params` and `normalize_response`: request the same hourly/daily variables, coordinates, `timezone=UTC` and `timeformat=unixtime`, with date padding from prediction date minus 15 days through prediction date; then select exactly the previous 14 complete days in the field's IANA timezone. Preserve source/profile, provider grid, units and retrieval metadata in storage. Only use complete available history; unavailable hours remain insufficient history. Provider model changes over time can still shift the data, so this remains experimental. Training continues to use the included static snapshot; this guide adds no download step or Python weather fetch.

[sql/002_ml_weather_history.sql](sql/002_ml_weather_history.sql) is an unapplied proposal for `ml_planting_context`, `ml_weather_hourly` and `ml_weather_daily`. [database.py](src/csd_weather/database.py) demonstrates conversion of those proposed rows into the request, including summing hourly sunshine into daily totals. These tables are not today's Java schema. Review the schema and implement a compatible collector/storage path separately; applying SQL alone supplies no historical data.

## 3. Add the smallest Java connection

Proposed request flow:

```text
Browser → authenticated Java crop route → ownership check
        → read compatible database history → POST Python /predict
        → Java saves result in database → return separate biomass result to browser
```

1. In `backend/src/main/java/com/csd/farm/crop/CropController.java`, add a proposed `POST /api/crops/{cropType}/prediction?plantedAt=...` route. Like the existing details route, pass `Principal.getName()` to the service. This route does not exist yet.
2. In `backend/src/main/java/com/csd/farm/crop/CropService.java`, follow the existing `farmerId(username)` and `findCrop(owner, cropType, normalizeTime(plantedAt))` pattern **before** reading history or calling Python. Read history scoped to that owner and planting. Build the complete request from trusted stored context; do not accept a browser-supplied farmer ID as authorization.
3. Add a small Java HTTP client, for example a new `crop/MlPredictionClient.java`, and a history repository/request mapper for the proposed history schema. Configure the Python base URL and optional private token on the server. Keep Python running as a separate process; do not launch training or a Python process per web request.
4. Let Java save the returned result against the verified planting using a prediction table/repository; retain status, units, model version and input hash as well as any number. Return a separate prediction response (new DTO or explicit field in `crop/CropResponses.java`). Do not put biomass into its existing `healthScore`. Keep `crop/CropHealthService.java` rules-based assessments separate. Frontend display work follows only after this route is tested. Python's existing local JSON save can remain; do not also enable its optional database uploader for this flow.

The essential HTTP operation can use Java's standard client. This illustrative fragment belongs inside that future client; `requestJson` must already contain the validated, owner-scoped history above:

```java
var client = java.net.http.HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofSeconds(5)).build();
var request = java.net.http.HttpRequest.newBuilder()
    .uri(java.net.URI.create("http://127.0.0.1:8001/predict"))
    .timeout(java.time.Duration.ofSeconds(30))
    .header("Content-Type", "application/json")
    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestJson));
// When ML_API_TOKEN is configured, add Authorization: Bearer <same server token>.
var response = client.send(request.build(),
    java.net.http.HttpResponse.BodyHandlers.ofString());
```

Reuse the client, serialize/parse JSON with the backend's Jackson support, and handle HTTP status codes and timeouts. HTTP 200 still requires checking the JSON `status`: `EXPERIMENTAL` can include a number; `UNSUPPORTED` and `UNAVAILABLE` should display a reason without a number. Malformed top-level requests and invalid nested history both return HTTP 422 with `INVALID_INPUT` and the message "Check the input and provide 14 complete days of weather history." Authentication can return 401/403 and readiness can return 503. A connection failure must become an unavailable result, not a fabricated score. Server-side ownership remains Java's responsibility; the Python API trusts its caller.

For a local school demo, both processes can run on the same computer. Keep the Python endpoint private and call it from Java, not the browser. The optional direct Python database job in the README is an alternative workflow; Java HTTP inference needs no Supabase credentials in Python.

## 4. Check each layer

Run the all/file/single-name commands in [README verification](README.md#verification). The existing `test_private_api_requires_token_and_saves_locally` in `ml/tests/test_service.py` checks API authentication and local saving; `test_fixture_matches_golden_features_and_prediction` in `ml/tests/test_database.py` checks stored-row conversion. These tests verify code behavior, not model accuracy, and neither proves Java is connected. Runnable now means the saved-fixture demonstration, not a working end-to-end application connection.

When the future Java route is implemented, add backend tests for another user's planting, incomplete history, Python timeout/unavailability, and a valid response whose biomass stays separate from health. First prove the HTTP connection with the archived example; then prove real owner-scoped database history satisfies the same contract.
