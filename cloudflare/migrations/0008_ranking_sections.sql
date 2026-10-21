-- Editions rank startups and scaleups separately. Each list is its own snapshot (positions 1–10 each), and existing
-- snapshots are startup lists. Older Workers ignore the column and only ever see startup snapshots of their own.
ALTER TABLE ranking_snapshots ADD COLUMN section TEXT NOT NULL DEFAULT 'startups' CHECK (section IN ('startups', 'scaleups'));
