-- DRAFT ONLY: not applied. Requires a separately authorized backend/collector integration.
-- Existing latest farm snapshots do NOT satisfy this history contract.
-- The backend must verify farmer/planting ownership against the application's real schema.
-- No assumed foreign key to auth.users or an unknown application planting table.
create table public.ml_planting_context (
    farmer_id bigint not null check (farmer_id > 0),
    crop_type text not null check (crop_type = 'WHEAT'),
    planted_at timestamptz not null,
    profile text not null check (profile = 'ifs-soil-0-7-v1'),
    variant text not null check (variant = 'winter_wheat'),
    growing_system text not null check (growing_system = 'outdoor_soil'),
    sowing_date date not null,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    timezone text not null, -- collector validates IANA name, e.g. Europe/Sofia
    soil_depth_cm text not null check (soil_depth_cm = '0-7'),
    primary key (farmer_id, crop_type, planted_at, profile)
);
create table public.ml_weather_hourly (
    farmer_id bigint not null,
    crop_type text not null,
    planted_at timestamptz not null,
    profile text not null,
    observed_at timestamptz not null, -- exact UTC hourly instant, including DST folds
    temperature_2m double precision not null check (temperature_2m > '-Infinity'::float8 and temperature_2m < 'Infinity'::float8), -- Celsius
    relative_humidity_2m double precision not null check (relative_humidity_2m between 0 and 100), -- percent
    vapour_pressure_deficit double precision not null check (vapour_pressure_deficit >= 0 and vapour_pressure_deficit < 'Infinity'::float8), -- kPa
    soil_moisture_0_to_7cm double precision not null check (soil_moisture_0_to_7cm between 0 and 1), -- m3/m3
    sunshine_duration double precision not null check (sunshine_duration between 0 and 3600), -- seconds
    primary key (farmer_id, crop_type, planted_at, profile, observed_at),
    foreign key (farmer_id, crop_type, planted_at, profile)
        references public.ml_planting_context (farmer_id, crop_type, planted_at, profile)
);
create table public.ml_weather_daily (
    farmer_id bigint not null,
    crop_type text not null,
    planted_at timestamptz not null,
    profile text not null,
    local_date date not null, -- calendar date in context.timezone
    daylight_duration double precision not null check (daylight_duration between 0 and 86400), -- astronomical seconds
    primary key (farmer_id, crop_type, planted_at, profile, local_date),
    foreign key (farmer_id, crop_type, planted_at, profile)
        references public.ml_planting_context (farmer_id, crop_type, planted_at, profile)
);
alter table public.ml_planting_context enable row level security;
alter table public.ml_weather_hourly enable row level security;
alter table public.ml_weather_daily enable row level security;
revoke all on public.ml_planting_context, public.ml_weather_hourly, public.ml_weather_daily from public, anon, authenticated;
grant select, insert, update on public.ml_planting_context, public.ml_weather_hourly, public.ml_weather_daily to service_role;
-- No public policies. The service role bypasses RLS, so ownership is a backend responsibility.
-- The trusted collector must persist compatible IFS source values, not relabel other depths/models.
-- Read requires all 14 complete local days; never fabricate missing VPD, RH or history.
