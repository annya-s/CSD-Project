# Crop conditions: real weather integration

## What the app does now

Opening the dashboard or crop details fetches real Open-Meteo data using each
planting's saved latitude and longitude. The teammate's EnvironmentClient and
EnvironmentService now provide a completed-day method for crop comparison.
The original /api/environment/latest endpoint is still available; the crop
comparison uses the new daily method, not that hourly endpoint.

The app compares **yesterday's daily mean temperature and total sunshine**, using
the timezone Open-Meteo resolves for the coordinates. These are weather-model
values, not field sensor measurements. Today's full-day forecast and a partial
day's sunshine are not used as completed-day conditions.

Humidity and soil moisture display real readings but stay **Not assessed**.
Their daily means require every hour to be available. The approved temperature
and sunlight reference ranges remain hardcoded.

## Soil moisture: what the percentage means

The teammate's API requests soil_moisture_3_to_9cm in **m³/m³**. We store that
value and multiply by 100 only for the assessment display:

    0.25 m³/m³ × 100 = 25% water by soil volume

This is **not 25% of available water depleted**, so it must not be compared with
potato's 35% depletion reference. A dryness assessment still needs the soil's
field capacity and wilting point, and data representative of the crop root zone.
The API's 3–9 cm layer is a shallow layer, not the entire root zone.

## Follow the code in this order

Java paths below are inside backend/src/main/java/com/csd/farm/.

| File | Responsibility |
| --- | --- |
| environment/EnvironmentClient.java | fetchCompletedDayData calls Open-Meteo with timeouts, past days, location timezone, and explicit variables. |
| environment/DailyEnvironmentResponse.java | Describes the returned JSON, including units and Unix timestamps. |
| environment/EnvironmentService.java | getCompletedDay selects yesterday, checks units and prepares DailyWeather. Complete hourly coverage is required for soil/RH means; missing values stay null. |
| crop/CropWeatherService.java | Reuses API results for 15 minutes per location, saves them for each owned planting, and handles outages. Failed requests are retried after a one-minute pause. |
| crop/CropWeatherRepository.java | Stores the latest snapshot. V3 records whether it came from OPEN_METEO or MANUAL. |
| crop/CropConditions.java | Stores the ten approved temperature and sunshine reference ranges. |
| crop/CropHealthService.java | Converts sunshine seconds to hours, soil water to display percent, compares temperature/sunlight, and generates review flags and suggestions. |
| crop/CropController.java | Checks crop ownership before fetching weather and supplies the same comparisons to both screens. |

The page scripts refresh every 60 seconds while visible. The backend's
15-minute cache prevents a fresh external API call on every page refresh.
Updates happen when pages request data; no background scheduler fetches weather
for farms whose pages are closed.

## Requests and returned data

The existing authenticated endpoints fetch weather automatically:

    GET /api/dashboard
    GET /api/crops/POTATO?plantedAt=2026-01-10T08%3A30%3A00Z

Responses include weatherSource, weatherMessage, weather, readings and
needsAttention. Details also include issues and recommendedActions. The browser
labels the provider, date and timezone.

| Weather field | Unit |
| --- | --- |
| temperatureMeanC | °C, daily mean air temperature at 2 m |
| sunshineDurationSeconds | Seconds, total for the complete local day |
| humidityMeanPercent | %, mean relative humidity at 2 m |
| soilMoistureMeanM3M3 | m³/m³, mean volumetric soil water content at 3–9 cm |

The soil entry in readings is converted to % by volume for presentation.
Raw weather snapshots retain m³/m³. Missing values are null, not zero.
Upstream unit metadata is checked before values are used.

## Failure handling and manual data

If Open-Meteo fails, the app shows a warning. It may show **previously saved
Open-Meteo data**, with its date, but never substitutes old manual/demo values.
Without saved API data, conditions are Not assessed. V3 marks pre-existing
snapshots as MANUAL because their source was not verified.

The manual POST /api/crops/{cropType}/weather?plantedAt=... endpoint returns
HTTP 409 while automatic fetching is enabled. For offline fixtures, set
WEATHER_API_ENABLED=false before startup. The UI identifies those readings as
manually supplied. Tests use this setting or stub the API; automated tests
do not call Open-Meteo.

## Run locally

In PowerShell, from backend:

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"

In Bash/WSL, use sh ./mvnw instead of .\mvnw.cmd.

The **demo profile only chooses an in-memory database**. Weather fetching is real
by default in both demo and normal Supabase profiles. Demo accounts, crops and
database readings disappear when the server stops. PostgreSQL records are
preserved by the migrations. Internet access to api.open-meteo.com is required.

Run .\mvnw.cmd test to check calculations, conversions, complete-day selection,
missing data, API failures, caching, persistence, CSRF and ownership.

Reference: [Open-Meteo API documentation](https://open-meteo.com/en/docs).
Crop assumptions: [crop-growing-conditions.md](crop-growing-conditions.md).

