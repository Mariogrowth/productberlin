-- Upcoming in-person Berlin startup and tech events from curated organisers' public calendars (event-sources.json).
-- Each collection replaces a source's upcoming events, so changed and cancelled events disappear. New tables only.
CREATE TABLE events (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    title TEXT NOT NULL,
    starts_at TEXT NOT NULL,
    ends_at TEXT,
    organiser TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('Startup', 'Tech')),
    venue TEXT,
    url TEXT
);
CREATE INDEX events_starts ON events (starts_at);
CREATE TABLE event_collections (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    collected_at TEXT NOT NULL
);
