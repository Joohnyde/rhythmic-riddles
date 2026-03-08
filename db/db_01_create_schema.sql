-- =====================================================================
-- RhythmicRiddles - Idempotent schema bootstrap (PostgreSQL)
-- Safe to run multiple times. Does NOT drop anything.
--
-- Notes:
-- - This is a schema file (tables/constraints/indexes/extensions).
-- - It assumes you're connected to the target database already.
-- - For patching/upgrades with data backfills, use separate patch scripts.
-- =====================================================================

-- Make sure we operate in public schema by default
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;
SET search_path = public;

-- ------------------------------------------------------------
-- Extensions
-- ------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';

-- ------------------------------------------------------------
-- Tables (CREATE TABLE IF NOT EXISTS is idempotent)
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.album (
    id uuid NOT NULL,
    name character varying NOT NULL,
    custom_question character varying
);

CREATE TABLE IF NOT EXISTS public.game (
    id uuid NOT NULL,
    date timestamp without time zone NOT NULL,
    stage integer DEFAULT 0 CONSTRAINT game_status_not_null NOT NULL,
    max_songs integer DEFAULT 10 NOT NULL,
    max_albums integer DEFAULT 10 NOT NULL,
    code character varying(4),
    password_hash character varying(128)
);

CREATE TABLE IF NOT EXISTS public.song (
    id uuid NOT NULL,
    authors character varying,
    name character varying,
    snippet_duration double precision,
    answer_duration double precision
);

CREATE TABLE IF NOT EXISTS public.team (
    id uuid NOT NULL,
    button_code text,
    game_id uuid,
    name character varying,
    image character varying
);

CREATE TABLE IF NOT EXISTS public.track (
    id uuid NOT NULL,
    album_id uuid,
    song_id uuid,
    custom_answer character varying
);

CREATE TABLE IF NOT EXISTS public.category (
    id uuid NOT NULL,
    album_id uuid NOT NULL,
    game_id uuid NOT NULL,
    picked_by_team_id uuid,
    ordinal_number integer,
    is_done boolean
);

CREATE TABLE IF NOT EXISTS public.schedule (
    id uuid NOT NULL,
    ordinal_number integer,
    track_id uuid,
    started_at timestamp without time zone,
    revealed_at timestamp without time zone,
    category_id uuid
);

CREATE TABLE IF NOT EXISTS public.interrupt (
    id uuid NOT NULL,
    schedule_id uuid,
    arrived_at timestamp without time zone,
    resolved_at timestamp without time zone,
    is_correct boolean,
    score_or_scenario_id integer,
    team_id uuid
);

-- ------------------------------------------------------------
-- Primary keys (guarded by constraint name)
-- ------------------------------------------------------------

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'album_pkey') THEN
    ALTER TABLE ONLY public.album
      ADD CONSTRAINT album_pkey PRIMARY KEY (id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'game_pkey') THEN
    ALTER TABLE ONLY public.game
      ADD CONSTRAINT game_pkey PRIMARY KEY (id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'song_pkey') THEN
    ALTER TABLE ONLY public.song
      ADD CONSTRAINT song_pkey PRIMARY KEY (id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'team_pkey') THEN
    ALTER TABLE ONLY public.team
      ADD CONSTRAINT team_pkey PRIMARY KEY (id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'track_pkey') THEN
    ALTER TABLE ONLY public.track
      ADD CONSTRAINT track_pkey PRIMARY KEY (id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'category_pkey') THEN
    ALTER TABLE ONLY public.category
      ADD CONSTRAINT category_pkey PRIMARY KEY (id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'schedule_pkey') THEN
    ALTER TABLE ONLY public.schedule
      ADD CONSTRAINT schedule_pkey PRIMARY KEY (id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'interrupt_pkey') THEN
    ALTER TABLE ONLY public.interrupt
      ADD CONSTRAINT interrupt_pkey PRIMARY KEY (id);
  END IF;
END $$;

-- ------------------------------------------------------------
-- Indexes (idempotent on modern Postgres)
-- ------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_interrupt_schedule_arrived_time
  ON public.interrupt USING btree (schedule_id, arrived_at DESC);

CREATE INDEX IF NOT EXISTS idx_interrupt_team_schedule
  ON public.interrupt USING btree (team_id, schedule_id);

CREATE INDEX IF NOT EXISTS idx_team_button_code
  ON public.team USING btree (button_code);

-- Note: index options like deduplicate_items require supported PG versions.
CREATE INDEX IF NOT EXISTS index_game_code
  ON public.game USING btree (code) WITH (deduplicate_items='true');

CREATE INDEX IF NOT EXISTS interrupt_index_arrived_desc
  ON public.interrupt USING btree (arrived_at DESC) WITH (deduplicate_items='true');

-- ------------------------------------------------------------
-- Foreign keys (guarded by constraint name)
-- ------------------------------------------------------------

DO $$
BEGIN
  -- team -> game
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_team_game') THEN
    ALTER TABLE ONLY public.team
      ADD CONSTRAINT fk_team_game FOREIGN KEY (game_id) REFERENCES public.game(id);
  END IF;

  -- track -> album, song
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_track_album') THEN
    ALTER TABLE ONLY public.track
      ADD CONSTRAINT fk_track_album FOREIGN KEY (album_id) REFERENCES public.album(id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_track_song') THEN
    ALTER TABLE ONLY public.track
      ADD CONSTRAINT fk_track_song FOREIGN KEY (song_id) REFERENCES public.song(id);
  END IF;

  -- category -> album, game, team
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_category_album') THEN
    ALTER TABLE ONLY public.category
      ADD CONSTRAINT fk_category_album FOREIGN KEY (album_id) REFERENCES public.album(id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_category_game') THEN
    ALTER TABLE ONLY public.category
      ADD CONSTRAINT fk_category_game FOREIGN KEY (game_id) REFERENCES public.game(id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_category_team') THEN
    ALTER TABLE ONLY public.category
      ADD CONSTRAINT fk_category_team FOREIGN KEY (picked_by_team_id) REFERENCES public.team(id);
  END IF;

  -- schedule -> category, track
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_schedule_category') THEN
    ALTER TABLE ONLY public.schedule
      ADD CONSTRAINT fk_schedule_category FOREIGN KEY (category_id) REFERENCES public.category(id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_schedule_track') THEN
    ALTER TABLE ONLY public.schedule
      ADD CONSTRAINT fk_schedule_track FOREIGN KEY (track_id) REFERENCES public.track(id);
  END IF;

  -- interrupt -> schedule, team
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_interrupt_schedule') THEN
    ALTER TABLE ONLY public.interrupt
      ADD CONSTRAINT fk_interrupt_schedule FOREIGN KEY (schedule_id) REFERENCES public.schedule(id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_interrupt_team') THEN
    ALTER TABLE ONLY public.interrupt
      ADD CONSTRAINT fk_interrupt_team FOREIGN KEY (team_id) REFERENCES public.team(id);
  END IF;
END $$;

-- ------------------------------------------------------------
-- Done
-- ------------------------------------------------------------
-- You can add a lightweight sanity check if you want:
-- SELECT 'schema_ok' AS status;
