-- ============================================================
-- V19__club_portal_branding.sql
-- Add branding columns to club_portal_settings for white-label portal
-- ============================================================

ALTER TABLE club_portal_settings
    ADD COLUMN IF NOT EXISTS logo_url           VARCHAR(500),
    ADD COLUMN IF NOT EXISTS primary_color_hex   VARCHAR(7),
    ADD COLUMN IF NOT EXISTS secondary_color_hex VARCHAR(7),
    ADD COLUMN IF NOT EXISTS portal_title        VARCHAR(100);
