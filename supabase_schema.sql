-- ============================================
-- Supabase Schema for Minimalist Launcher
-- Run this in your Supabase SQL Editor
-- ============================================

-- Device identification
CREATE TABLE IF NOT EXISTS devices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id TEXT UNIQUE NOT NULL,
    device_model TEXT,
    android_version TEXT,
    app_version TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ DEFAULT NOW()
);

-- App launch events: every time the user opens an app
CREATE TABLE IF NOT EXISTS app_launches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id TEXT NOT NULL,
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    launched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    launch_source TEXT DEFAULT 'unknown', -- 'favorites', 'drawer', 'search'
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Screen time: daily aggregated usage per app
CREATE TABLE IF NOT EXISTS screen_time (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id TEXT NOT NULL,
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    date DATE NOT NULL,
    usage_duration_ms BIGINT NOT NULL DEFAULT 0, -- milliseconds
    open_count INT NOT NULL DEFAULT 0,
    first_used_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (device_id, package_name, date)
);

-- Indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_app_launches_device ON app_launches(device_id);
CREATE INDEX IF NOT EXISTS idx_app_launches_time ON app_launches(launched_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_launches_package ON app_launches(device_id, package_name);
CREATE INDEX IF NOT EXISTS idx_screen_time_device_date ON screen_time(device_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_screen_time_package ON screen_time(device_id, package_name, date DESC);

-- Enable Row Level Security
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_launches ENABLE ROW LEVEL SECURITY;
ALTER TABLE screen_time ENABLE ROW LEVEL SECURITY;

-- Policies: allow all operations using the anon/service key
-- Adjust these for your security requirements
CREATE POLICY "Allow all on devices" ON devices FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on app_launches" ON app_launches FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all on screen_time" ON screen_time FOR ALL USING (true) WITH CHECK (true);
