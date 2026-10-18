-- Items from startup publishers' feeds for the weekly news. They are judged like Google News articles (catalogue names
-- in the headline, keyword categories) when the news is built. Every item is stored once. New table only.
CREATE TABLE feed_items (
    key TEXT PRIMARY KEY,
    feed TEXT NOT NULL,
    source TEXT NOT NULL,
    title TEXT NOT NULL,
    translated_title TEXT,
    link TEXT NOT NULL,
    published_at TEXT NOT NULL,
    stored_at TEXT NOT NULL
);
CREATE INDEX feed_items_published ON feed_items (published_at);
