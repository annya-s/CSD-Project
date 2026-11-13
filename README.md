# Dome — AI crop-health assistant

Farmers record their crops (type, planting date, location). Dome pulls live weather
for each planting, checks it against the crop's ideal ranges, estimates growth and
yield, and asks an AI model for a short recommendation with a confidence score.

- **Weather:** Open-Meteo (live daily readings and 14-day averages per planting)
- **Health check:** rule-based comparison against per-crop ranges (`CropHealthService`)
- **Biomass:** documented formula, with the shortfall broken down by cause ([docs](docs/biomass-formula.md))
- **Yield outlook:** CatBoost model trained on FAOSTAT yields and ERA5 weather ([ml/README.md](ml/README.md))
- **AI recommendation:** Google Gemini, scored for confidence against the rule-based check

Stack: Spring Boot 4 (Java 17), Supabase PostgreSQL, plain HTML/CSS/JS served by
Spring, Python 3.12 for the ML model. Ten crops: potato, sugar cane, apple, rice,
wheat, maize, tomato, carrot, lettuce, soybean.

Deployed backend (Render, Docker): <https://csd-project-g98a.onrender.com>

## Run locally

Needs **Java 17+**. Maven comes with the wrapper. Commands are for PowerShell; on
macOS/Linux use `sh ./mvnw` instead of `.\mvnw.cmd`.

### Quick demo (in-memory database, no Supabase)

```powershell
cd backend
$env:GOOGLE_API_KEY = "your-gemini-key"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Open <http://127.0.0.1:8080> and create an account. Data is lost when the app stops.
Weather is still fetched live.

### With Supabase

Create `backend/application-local.properties` (it is gitignored, so never commit it):

```properties
DB_URL=jdbc:postgresql://YOUR-SESSION-POOLER-HOST:5432/postgres?sslmode=require
DB_USERNAME=postgres.YOUR-PROJECT-REF
DB_PASSWORD=YOUR-DATABASE-PASSWORD
COOKIE_SECURE=false
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=your-gmail-app-password
```

Then run `.\mvnw.cmd spring-boot:run` from `backend`. Use the **Session pooler**
details from Supabase → Connect, and your database password (not an API key).

Flyway is disabled. The app expects the existing `public` schema in Supabase
(`USERS`, `CROP_CYCLES`, `FARMS`, weather tables…). The test fixture
[`public-schema.sql`](backend/src/test/resources/db/ci/public-schema.sql) documents
the tables and columns the code relies on.

### Yield model (optional)

The backend runs `ml/yield_predict.py` and auto-detects `ml/.venv`. Without it, the
crop page shows "yield model unavailable" and everything else works.

```powershell
py -3.12 -m venv ml\.venv
ml\.venv\Scripts\python.exe -m pip install -r ml\requirements.txt
```

### Configuration

Set these as environment variables or in `application-local.properties`.

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Yes (not demo) | Supabase PostgreSQL connection |
| `GOOGLE_API_KEY` | Yes | Gemini key for AI recommendations |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | For email | Gmail SMTP for verification, 2FA and password reset |
| `COOKIE_SECURE` | No (default `true`) | Set `false` for local HTTP |
| `WEATHER_API_ENABLED` | No (default `true`) | `false` = enter weather manually |
| `PORT` | No (default `8080`) | Server port |
| `DB_USER_*` | No | Override `USERS` table/column names |

## Run with Docker

The image contains the backend and the Python yield model; this is how Render deploys it.

```powershell
docker build -t dome .
docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... -e GOOGLE_API_KEY=... dome
```

## How the biomass formula works

The crop page shows two numbers for the crop's full-growth date (planting date + the
crop's `maturity_days`):

- **Full-growth target:** the crop's `mature_target`, i.e. biomass under ideal conditions.
- **Estimate if recent weather continues:** `mature_target × factor`, where

```
factor = clip(1 − 0.025·|T − Tref| − 2·|M − Mref| − 0.12·|V − Vref| − 0.025·|S − Sref|, 0, 1)
```

| Symbol | Input (last 14-day average) | Unit |
| --- | --- | --- |
| T | Mean temperature | °C |
| M | Soil moisture, 3–9 cm | m³/m³ |
| V | Vapour pressure deficit | kPa |
| S | Daily sunshine | hours |

Each crop's ideal values (`ref`), target and maturity age are in
[`crop-profiles.json`](backend/src/main/resources/crop-profiles.json). Each weather
factor subtracts its own penalty, so the shortfall splits exactly by cause. For example:
"18% below target: 12% from soil moisture too low, 4% from temperature too high…".
If the penalties add up to more than 100%, each one is scaled down proportionally.

- **Missing data:** if the 14-day averages are missing, stale or out of range, only the
  target is shown (`REFERENCE_ONLY`). Crops without a profile return `UNSUPPORTED`.
- **Limits:** the coefficients and ideal values are assumptions, not measurements.
  Penalties are symmetric, so too wet counts the same as too dry. Earlier weather and
  farm management are ignored. The result is an estimate, not a harvest forecast.

Code: `BiomassFormula.java`, `BiomassEstimateService.java`, tests in
`BiomassFormulaTest.java`. Full details and sources: [docs/biomass-formula.md](docs/biomass-formula.md).

## Accounts and roles

Spring Security with session cookies and CSRF protection. Passwords are hashed
with PBKDF2. Sessions expire after 30 minutes. Optional email verification and
2FA codes are sent by email.

- **FARMER** (default): manages their own crops only.
- **ADMIN**: user administration page. Set `USERS."Role"` to `ADMIN` in the database.

## API

Every endpoint except `csrf`, `register`, `login`, `verify-email`, `forgot-password`
and `reset-password` needs the session cookie. POST/DELETE requests also need a CSRF token: fetch `GET /api/auth/csrf`
and send `token` in the returned `headerName`. The frontend's `api.js` does this for you.

| Area | Endpoints |
| --- | --- |
| Auth `/api/auth` | `GET csrf`, `POST register` (JSON `username`, `email`, `password`), `POST login` (form `username`, `password`, optional `code`), `POST logout`, `GET me` |
| Account `/api/auth` | `POST verification-code`, `verify-email`, `forgot-password`, `reset-password`, `username`, `email`, `password`, `two-factor` |
| Crops `/api` (FARMER) | `GET crop-types`, `GET dashboard`, `GET/POST crops`, `GET/DELETE crops/{cropCycleId}` |
| Crop analysis (FARMER) | `POST crops/{id}/weather` (manual reading), `POST crops/{id}/prediction` (biomass + yield), `POST crops/{id}/assessment` (AI recommendation) |
| Admin `/api/admin` (ADMIN) | `GET check`, `GET users`, `POST users/{id}/password`, `POST users/{id}/unverify`, `POST users/{id}/disable2fa`, `DELETE users/{id}` |
| Environment | `GET /api/environment/latest?latitude=…&longitude=…` |

New crop body: `{"farmName": "North field", "cropType": "POTATO", "plantedAt": "2026-06-10T08:30:00Z", "latitude": 1.3521, "longitude": 103.8198}`

Errors: `400` invalid input, `401` not logged in, `403` bad CSRF token or wrong
role, `404` crop not found or not yours, `409` duplicate.

## Project layout

```text
backend/src/main/java/com/csd/farm/
  ai/            Gemini recommendation service
  auth/          Accounts, login, email codes, 2FA
  admin/         User administration API
  crop/          Crops, weather, health check, biomass, yield model client
  environment/   Open-Meteo client
  config/        Security and page routing
backend/src/main/resources/static/   Pages: login, dashboard, crop-details, new-entry, account, admin (+ api.js, styles.css)
ml/              Yield model: training, prediction, tests
docs/            Biomass formula, crop growing conditions, ML research
frontend/        Unfinished Vue prototype (not used)
```

## Tests and CI

GitHub Actions ([backend-ci.yml](.github/workflows/backend-ci.yml)) runs on pushes to
`main` and on every PR. It starts PostgreSQL 17, loads the test schema, runs
`mvnw clean verify` and uploads the JAR and test reports. No Supabase credentials needed.

To run the same tests locally (needs Docker; the database must be disposable):

```powershell
docker run -d --name csd-ci-db -p 127.0.0.1:15432:5432 -e POSTGRES_DB=farm_ci -e POSTGRES_USER=farm_ci -e POSTGRES_PASSWORD=ci_only postgres:17
cd backend
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/farm_ci'
.\mvnw.cmd clean verify
docker rm -f -v csd-ci-db
```

ML tests: `ml\.venv\Scripts\python.exe -m pytest ml\tests -q`

## Known limitations

- The yield model does not beat a "normal year" baseline on the 2022–2024 test
  years. It uses country-level data and one weather point per country (see [ml/README.md](ml/README.md)).
- The weather tables keep only the latest reading and the latest 14-day average per crop.
- The AI confidence score is a keyword check against the rule-based assessment, not a calibrated probability.

## More docs

- [Biomass formula](docs/biomass-formula.md)
- [Crop growing conditions and sources](docs/crop-growing-conditions.md)
- [Crop health integration](docs/crop-health-integration.md)
- [ML research plan](docs/ml/README.md) · [Yield model](ml/README.md)
