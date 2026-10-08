-- English translation of a non-English headline, shown to readers. Matching and ranking use the original headline.
-- Nullable and unused by older Workers, so it is safe to apply before deploying the Worker that writes it.
ALTER TABLE news_articles ADD COLUMN translated_headline TEXT;
