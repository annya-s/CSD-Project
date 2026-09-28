# Supabase authentication setup

The backend connects directly to PostgreSQL. The project API URL is
https://tsrxszdtwtnypidekwhy.supabase.co; it is not the database JDBC URL.
This uses the existing Spring Security login, not Supabase Auth or a browser API key.

## Confirm your table first

The user table is `USERS` with `User_ID` of type int8 (bigint). This update
uses Java Long account IDs throughout authentication, token ownership and crops.
The database generates IDs; the backend never uses MAX(id)+1 for registration.
The default schema is assumed to be `public`; change DB_USER_SCHEMA if needed.
Column identifiers are quoted exactly as supplied: `User_ID`, `User_Name`,
`User_Email`, `Password_Hash`.

Run this read-only query in Supabase SQL Editor to inspect your actual schema:

```sql
SELECT table_schema, table_name, column_name, data_type, column_default
FROM information_schema.columns
WHERE lower(column_name) IN ('user_id','user_name','user_email','password_hash')
ORDER BY table_schema, table_name, ordinal_position;
```

Requirements: User_ID is a bigint primary key with an identity/sequence default; User_Name and User_Email are text
columns large enough for 40 and 254 characters; Password_Hash can hold at least
96 characters (255 recommended). Passwords use the original salted PBKDF2 format.
Existing hashes made by this app remain compatible; hashes from unrelated encoders
are not automatically converted. No plaintext passwords are stored or returned.

## One-time database setup

Edit the six identifiers at the top of `backend/sql/supabase-setup.sql` to match
your existing table exactly, then run it in Supabase SQL Editor. If User_ID has no default, it adds an identity generator under a table lock, starting above existing IDs. It creates unique
username/email indexes and auxiliary tables in `farm`, referencing your existing
user table. `account_status` stores verification state without requiring a fifth
column on your user table. Tokens and crop records use the same remote database.
Existing users receive an unverified status record.

The setup is transactional and intended to run once. If auxiliary farm tables
already exist from an earlier version, do not delete them or run the old Flyway
migration: their foreign keys and existing account IDs must be reconciled with
the real user table first. The script deliberately fails instead of overwriting
those tables. Duplicate usernames/emails must be resolved before unique indexes
can be created. The supplied SQL has not been run on your live database.

The setup enables row-level security and revokes browser-role access to the user table, including when it
is in the public schema. Use a trusted backend database role with access to the
user table and farm tables; never give database credentials to browser code.

## Connection configuration

Copy `backend/application-local.properties.example` to
`backend/application-local.properties` and fill in:

- DB_URL: `jdbc:postgresql://<host-from-Connect>:5432/postgres?sslmode=require`
- DB_USERNAME: copy the exact username from Supabase Connect; a shared session
  pooler commonly uses `postgres.tsrxszdtwtnypidekwhy`.
- DB_PASSWORD: your database password, not an anon or service-role API key.
- DB_USER_SCHEMA and DB_USER_TABLE: your real schema and table.
- DB_USER_ID_COLUMN, DB_USER_NAME_COLUMN, DB_USER_EMAIL_COLUMN,
  DB_USER_HASH_COLUMN: exact column names, without SQL quotes. Defaults match the
  supplied mixed-case names. Set lowercase names if that is what SQL reports.
- COOKIE_SECURE=false for localhost HTTP; use true for deployed HTTPS.

These may also be set as environment variables. Do not enable the demo profile
when connecting to Supabase. Normal startup has no H2 fallback and does not run
legacy migrations. Local credentials and build output are excluded from this ZIP.

Supabase's official connection guide:
https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot
Use the session pooler for an IPv4 local environment; copy its host from Connect.

## Run

Install Java 17 or newer, then from `backend` run:

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8080. Create an account using username, email and password.
The dashboard now greets the username. Verify the row in your actual Supabase
table; Password_Hash should contain a hash, never the submitted password. Stop
and restart the backend and log in again to confirm remote persistence.

The existing email delivery remains a development stub: verification and reset
codes are printed in the backend terminal, not sent by email. Login retains the
original behavior of allowing unverified accounts. Password reset requires email
verification through `/verify-email.html`. Real email delivery is outside this
persistence update. Sessions remain server memory and require login after restart.

## Tests

`.\mvnw.cmd test` uses an isolated H2 PostgreSQL-mode test database with the exact
mixed-case columns; it never connects to Supabase. Tests cover registration,
password hashing, login, duplicate accounts, CSRF, and crop ownership. The explicit
`demo` profile also uses disposable H2 data and is only for offline demonstrations.

## Changed files

- FarmerRepository.java: configurable quoted user-table mapping and remote writes.
- Farmer.java / AuthController.java: database-generated Long IDs and username/email/password registration;
  atomic account and token creation; no display-name persistence.
- AccountTokenRepository.java / CropRepository.java / CropController.java: bigint account references.
- AccountTokenService.java / SecurityConfig.java: transactional account updates
  and access to the existing email verification page script and endpoint.
- login.html / login.js / api.js: remove display-name input and greet username.
- ApiExceptionHandler.java: duplicate-email conflict message.
- application*.properties: explicit remote configuration and isolated demo/test setup.
- sql/supabase-setup.sql: non-destructive, transactional auxiliary schema setup.
- db/demo/schema.sql and integration tests: exercise the supplied column names.

The original db/migration/V1 file is retained for history only and is disabled.



Validation: 36 automated tests passed (35 integration tests and one database test), including bigint IDs above 2^31, hashing/login, duplicate email, verification/reset and crop ownership. Live Supabase connectivity and the PostgreSQL setup script have not been executed against your database.

