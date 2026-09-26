# CSD Project — crop records

A small Spring Boot application based on the four supplied mockups: login/sign-up,
farm dashboard, crop details, and a new-entry form. It uses plain HTML, CSS and
JavaScript, Spring Security, readable JDBC queries, and Supabase PostgreSQL.
The previous standalone prototype is preserved in `dist/index.html`.

## Run locally without Supabase

Install **Java 17 or newer**. Maven is included through the Maven wrapper.
From PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Open <http://127.0.0.1:8080>, create an account, and add a crop. This explicit demo
profile uses an **in-memory H2 database**: accounts and entries disappear when
the app stops. No demo passwords or farmer records are seeded. On macOS/Linux,
use `sh ./mvnw` instead of `.\mvnw.cmd`.

If an older demo server starts rejecting crop entries after it has been running
for a while, restart it with the updated project. H2 is pinned to 2.5.250 to fix
[H2 issue #4302](https://github.com/h2database/h2database/issues/4302): the old
version could reject valid crop types after a database connection closed.
Restarting the in-memory demo also clears its accounts and entries.

## Crop growing-condition research

See [the crop reference guide](docs/crop-growing-conditions.md) for all ten crops:
weather, temperature, air humidity, soil pH, soil moisture, and annual rainfall,
with direct sources and guidance on interpreting the units. These references
are research for the crop-details feature; they are not yet live assessment rules.

## Connect your Supabase PostgreSQL database

1. Create/open your Supabase project. In **Connect → Session pooler**, copy the
   host, database name, port and username. Use your **database password**, not
   an API key. The session pooler is suitable for an IPv4 development machine.
2. Inside `backend`, copy `application-local.properties.example` to
   `application-local.properties` and replace the placeholders. This file is
   ignored by Git. The URL should look like:

   ```properties
   DB_URL=jdbc:postgresql://YOUR-SESSION-POOLER-HOST:5432/postgres?sslmode=require
   DB_USERNAME=postgres.YOUR-PROJECT-REF
   DB_PASSWORD=YOUR-DATABASE-PASSWORD
   COOKIE_SECURE=false
   ```

3. Start the app **without** the demo profile:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

Flyway automatically creates the `farm` schema and tables on first startup.
The configured database user needs permission to create that schema and its
tables. There is no silent fallback to demo storage: invalid Supabase settings
make normal startup fail. No live Supabase connection is included in this repo.

You may supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as environment
variables instead. The browser only calls Spring Boot; credentials are never
sent to JavaScript. Keep the `farm` schema out of Supabase's exposed API schemas.
For deployment, use HTTPS and set `COOKIE_SECURE=true` (the default). The example
uses `sslmode=require`; for certificate verification, use `sslmode=verify-full`
and the Supabase CA certificate as described in their connection documentation.

This implementation stores application accounts in PostgreSQL and uses Spring
Security for login. It does **not** use the separate Supabase Auth service.
Sessions expire after 30 minutes of inactivity and are cleared on server restart.
Password reset, email verification, login rate limiting, and shared sessions for
multiple server instances are not implemented in this first version.

Official references: [Supabase connection settings](https://supabase.com/docs/guides/database/connecting-to-postgres)
and [Spring Security CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## Data model and identity

```text
farmer_account (one) ──── (many) crop_entry
  id (primary key)               farmer_id (foreign key)
                               crop_type
                               planted_at
                               latitude / longitude
```

An entry has **one composite primary key** containing three columns:
`(farmer_id, crop_type, planted_at)`. This implements the clarified requirement:
two farmers planting the same crop at the same time still create separate entries.
Within one farmer's account, the same crop and planting time cannot be entered twice.
Farmer ID comes from the authenticated session, never a browser-supplied owner ID.
The database enforces the foreign key and primary key, including concurrent writes.

Planting time is a timezone-aware timestamp stored at **one-second precision**.
The browser accepts local time and sends UTC. Equivalent instants with different
timezone offsets count as the same time; subsecond values are truncated before
saving. A farmer cannot record two separate plots of the same crop at the same
second with this key; supporting that later would require a plot/entry identifier.

The ten hardcoded choices are **Potato, Sugar cane, Apple, Rice, Wheat, Maize,
Tomato, Carrot, Lettuce, and Soybean**. These are choices in the crop catalogue,
not ten fabricated plantings. New accounts start with an empty dashboard.

Health readings, scores, weather, photos and AI/chatbot services have no supplied
data source. The UI explicitly shows **Not measured** / **Not assessed**. Dashboard
summaries are deterministic text, not AI output. No agronomic readings or advice
are invented.

## API

All crop endpoints require the login session cookie. Mutating requests also
require a CSRF token. Fetch `GET /api/auth/csrf` and send the returned `token`
using the returned `headerName`; fetch a fresh token after login or logout.
The included frontend does this automatically.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/auth/csrf` | Get a CSRF token, including before login |
| POST | `/api/auth/register` | JSON: `username`, `displayName`, `password` |
| POST | `/api/auth/login` | Form-encoded `username` and `password`; sets session cookie |
| GET | `/api/auth/me` | Current farmer, without password data |
| POST | `/api/auth/logout` | Invalidate session |
| GET | `/api/crop-types` | The 10 crop options |
| GET | `/api/dashboard` | Summary and this farmer's entries |
| GET | `/api/crops` | This farmer's entries |
| POST | `/api/crops` | Save an entry |
| GET | `/api/crops/{cropType}?plantedAt=…` | Details for an owned entry |

Example new-entry body:

```json
{
  "cropType": "POTATO",
  "plantedAt": "2026-06-10T08:30:00Z",
  "latitude": 1.3521,
  "longitude": 103.8198
}
```

Usernames contain 3–40 lowercase letters, digits, or underscores. Passwords are
10–128 characters and stored using salted PBKDF2 hashes. Coordinates must be in
range with at most six decimal places. Planting dates cannot be in the future.

Common responses: `400` invalid input, `401` login required or incorrect
credentials, `403` missing/expired CSRF token, `404` missing/unowned crop,
`409` duplicate username or planting. Login/logout return `204`; creation
returns `201`. Encode query parameters with `URLSearchParams`, especially when
timestamps include a `+` timezone offset.

## Code map

Each screen has its own HTML file (layout) and JavaScript file (behaviour), all
inside `backend/src/main/resources/static`. No Node.js or frontend build is needed.

| Screen | HTML | JavaScript | Main backend requests |
| --- | --- | --- | --- |
| Login and sign-up | `login.html` | `login.js` | `/api/auth/me`, `/api/auth/register`, `/api/auth/login` |
| Dashboard | `dashboard.html` | `dashboard.js` | `/api/dashboard`, `/api/crop-types` |
| Crop details | `crop-details.html` | `crop-details.js` | `/api/crops/{cropType}?plantedAt=…`, `/api/crop-types` |
| Add a crop | `new-entry.html` | `new-entry.js` | `/api/crop-types`, POST `/api/crops` |

The page scripts import `api.js` for shared requests, login checks, logout, and
small display helpers. All pages share `styles.css`. Each teammate can edit their
own HTML/JavaScript pair while using the same Java backend.

`/` and the old `/index.html` address redirect to `/login.html`. An already logged-in
farmer goes straight to the dashboard. A dashboard card links to
`/crop-details.html?type=POTATO&plantedAt=...`, so refreshing or bookmarking a crop
works. If login is required, the app returns to that page after login. The former
combined `index.html` and `app.js` have been replaced by these page files.

For crop-details work, start with `crop-details.html` and `renderDetails()` in
`crop-details.js`. Its `start()` function requests data from the existing Java
`CropController.details()` endpoint. Frontend files call API URLs; they do not
directly import or execute Java files. Photos and health assessment remain the
same placeholders as before this file reorganisation.

```text
backend/
  src/main/java/com/csd/farm/
    auth/                 Farmer accounts and sign-up
    config/               Homepage redirect, session authentication and request security
    crop/                 Crop catalogue, input validation, queries and API
    api/                  Friendly API errors
  src/main/resources/
    db/migration/         Versioned SQL table definitions
    static/               Four HTML/JS page pairs, shared api.js and styles.css
    application.properties
    application-demo.properties
  src/test/               Integration tests using H2 in PostgreSQL mode
```

The normal profile uses the PostgreSQL driver; the test/demo profiles use H2.
H2 verifies the application flow and migration structure, but it does not replace
testing a real Supabase connection with your project's credentials.

## Verify

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package
```

Tests cover registration and password hashing, login/logout, real CSRF tokens,
validation, database round-trips, crop details, duplicate keys, timezone
equivalence, and separation between farmers. `CropDatabaseTest` also closes the
connection that created the tables, then saves all ten crop types using fresh
connections. This catches the demo database regression without a long wait.
The packaged app is
`backend/target/farm-0.0.1-SNAPSHOT.jar` and can run with
`java -jar target/farm-0.0.1-SNAPSHOT.jar` from the `backend` directory after
configuring Supabase.
