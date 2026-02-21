-- =====================================================================
-- RhythmicRiddles - Idempotent baseline DATA seed (PostgreSQL)
-- Safe to run multiple times. Does NOT delete or overwrite user data.
--
-- Strategy:
-- - Uses ON CONFLICT DO NOTHING for tables that have a PK (id) already.
-- - This prevents duplicate inserts when rerun.
-- - IMPORTANT: This assumes your schema has PRIMARY KEY(id) on each table.
--
-- Notes:
-- - This is meant for initial/baseline content (system-provided rows).
-- - If you need to UPDATE baseline rows later, you should add an "is_system"
--   flag or use explicit guarded updates.
-- =====================================================================

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET client_min_messages = warning;
SET row_security = off;
SET search_path = public;

-- ------------------------------------------------------------
-- album
-- ------------------------------------------------------------
INSERT INTO public.album (id, name, custom_question) VALUES
  ('edf940fb-250d-4f5a-ad61-f1d4f254a435', 'Album1', NULL),
  ('3c8ed2c0-df3d-4639-b6a6-81550e473cca', 'Album2', NULL),
  ('c18a96dd-4991-4e28-9c04-d61ca05bc81b', 'Album3', NULL),
  ('6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'Album4', NULL),
  ('11f109d1-e4ac-4dd8-99fe-108035273677', 'Album5', NULL)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------
-- game
-- ------------------------------------------------------------
INSERT INTO public.game (id, date, stage, max_songs, max_albums, code, password_hash) VALUES
  ('b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', '2025-12-05 23:09:25.405306', 3, 2, 3, 'AKKU', NULL),
  ('aac328fb-be69-4f36-aa2d-a2839acf8360', '2025-12-02 17:34:57.56428', 3, 1, 3, 'NORR', NULL)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------
-- team
-- ------------------------------------------------------------
INSERT INTO public.team (id, button_code, game_id, name, image) VALUES
  ('17599f50-9e9d-4080-9077-b045e560db37', '123',  'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', 'Treci',   'https://i.imgur.com/OorBALw.png'),
  ('7f0612f5-cb1d-4665-8424-ef0027f355c7', '124',  'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', 'Luka',    'https://i.imgur.com/rV2ENEv.png'),
  ('9e604591-7a9f-4370-ac6f-595f6147498c', '125',  'aac328fb-be69-4f36-aa2d-a2839acf8360', 'Nazis',   'https://i.imgur.com/qoFrBsU.png'),
  ('e9c7ce08-a8cd-41bd-8716-37d774ab0132', '215',  'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', 'Deni',    'https://i.imgur.com/7oulgAX.png'),
  ('77272fb1-09f0-4db1-a410-b6f467441c22', '1671', 'aac328fb-be69-4f36-aa2d-a2839acf8360', 'Sugavac', 'https://i.imgur.com/p4E6G89.png')
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------
-- category
-- ------------------------------------------------------------
INSERT INTO public.category (id, album_id, game_id, picked_by_team_id, ordinal_number, is_done) VALUES
  ('2bbd4516-e0f1-492b-a12f-bc3cde999dc1', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false),
  ('01766a15-e6bc-444a-a5a7-cd1b8f48ef40', '11f109d1-e4ac-4dd8-99fe-108035273677', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false),
  ('2d3958b2-b03e-4bfd-844a-54e507e95043', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false),
  ('68f384d3-e437-45e3-b879-cc2cd9a31be3', 'c18a96dd-4991-4e28-9c04-d61ca05bc81b', 'b02ae71d-4f48-495d-9fa0-245c6d4ffb2c', NULL, NULL, false),
  ('647594c1-a18a-42c7-b59c-07ffb1d06d32', 'edf940fb-250d-4f5a-ad61-f1d4f254a435', 'aac328fb-be69-4f36-aa2d-a2839acf8360', NULL, NULL, false),
  ('ac7157fd-47a7-4848-b8b5-e8741cb0a5f6', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'aac328fb-be69-4f36-aa2d-a2839acf8360', NULL, NULL, false),
  ('ec6c9cf3-c759-4a12-9763-530bb76e9f54', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', 'aac328fb-be69-4f36-aa2d-a2839acf8360', NULL, NULL, false)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------
-- song
-- ------------------------------------------------------------
INSERT INTO public.song (id, authors, name, snippet_duration, answer_duration) VALUES
  ('c041398e-8e63-40ed-8f17-d7f1ca8ca405', 'Relja Torinno', 'i8',       9.01, 6),
  ('51ebda2d-056e-407c-8a71-352a88fa0136', 'All Time Low',  'Monsters', 11.75, 6.03),
  ('5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', 'Zdravko Colic', 'Flamingosi',11.54, 6.97)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------
-- track
-- ------------------------------------------------------------
INSERT INTO public.track (id, album_id, song_id, custom_answer) VALUES
  ('8c2a62d9-271a-4a36-bb22-5fb6b74d28c7', 'edf940fb-250d-4f5a-ad61-f1d4f254a435', '5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', NULL),
  ('b4e9173e-e68c-465e-b155-98cc2a01c3e8', 'edf940fb-250d-4f5a-ad61-f1d4f254a435', 'c041398e-8e63-40ed-8f17-d7f1ca8ca405', NULL),
  ('1cd1bb6e-0e26-4144-a4a4-ab2f5db3aaa2', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', '5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', NULL),
  ('929325b4-3bcf-4d58-b7d8-b9fcff347781', 'c18a96dd-4991-4e28-9c04-d61ca05bc81b', '5a5096a5-14ed-4b46-ae6f-75ce8ed8df70', NULL),
  ('4485e157-bf42-46f0-a961-494a7686e13c', 'c18a96dd-4991-4e28-9c04-d61ca05bc81b', 'c041398e-8e63-40ed-8f17-d7f1ca8ca405', NULL),
  ('f77d94e0-dfdf-4ca9-ae0b-dad8b81c85df', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', 'c041398e-8e63-40ed-8f17-d7f1ca8ca405', NULL),
  ('02ae2162-6180-4ede-80d5-79b536073524', '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL),
  ('1d0131ca-1417-4b60-b524-51b25652e997', '11f109d1-e4ac-4dd8-99fe-108035273677', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL),
  ('8168ba9e-68c6-4e98-aa47-7d28e72b33ab', '3c8ed2c0-df3d-4639-b6a6-81550e473cca', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL),
  ('a6aa901c-2cad-4571-81ff-398ee935b10a', '11f109d1-e4ac-4dd8-99fe-108035273677', '51ebda2d-056e-407c-8a71-352a88fa0136', NULL)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------
-- schedule, interrupt (empty in dump) - nothing to seed
-- ------------------------------------------------------------

-- Optional sanity check:
-- SELECT 'seed_ok' AS status;
