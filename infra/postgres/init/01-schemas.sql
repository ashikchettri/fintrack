-- Runs once on first container start. Schema-per-service isolation (see ARCHITECTURE.md §6).
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS finance;
