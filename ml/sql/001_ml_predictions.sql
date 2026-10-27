-- Run manually in the intended Supabase project after reviewing its existing schema.
-- This file has NOT been applied. It does not alter the current farm tables.
-- farmer_id is the existing application's BIGINT identity, not an assumed auth.uid().
-- Only the trusted backend may validate ownership and export predictions.
create table if not exists public.ml_predictions (
    id uuid primary key,
    farmer_id bigint not null check (farmer_id > 0),
    crop_type text not null,
    planted_at timestamptz not null,
    prediction_date date not null,
    model_version text not null,
    status text not null check (status in ('EXPERIMENTAL', 'UNAVAILABLE', 'UNSUPPORTED', 'INSUFFICIENT_HISTORY', 'INSUFFICIENT_CONTEXT')),
    payload jsonb not null,
    created_at timestamptz not null default now()
);
create index if not exists ml_predictions_planting_date_idx
    on public.ml_predictions (farmer_id, crop_type, planted_at, prediction_date desc);
alter table public.ml_predictions enable row level security;
revoke all on public.ml_predictions from anon, authenticated;
grant select, insert on public.ml_predictions to service_role;
-- No browser-facing policy is created. The service secret bypasses RLS.
-- Connecting the existing Java application requires a separate authorized integration.
