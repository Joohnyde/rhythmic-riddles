-- =====================================================================
-- RhythmicRiddles - Runtime integrity invariants
--
-- Upgrade patch for databases created before these invariants were part
-- of db_01_create_schema.sql. Safe to run multiple times.
--
-- The script intentionally fails if existing data already violates one
-- of the invariants. Resolve that data first instead of silently dropping
-- or rewriting live game state.
-- =====================================================================
SET search_path = public;

-- Picked categories have a unique progression ordinal within a game.
-- NULL ordinals remain allowed for categories that have not been picked.
CREATE UNIQUE INDEX IF NOT EXISTS uq_category_game_ordinal
  ON public.category USING btree (game_id, ordinal_number)
  WHERE ordinal_number IS NOT NULL;

-- At most one team can actively answer a schedule at a time.
-- System pauses are intentionally excluded so they can nest around a team answer.
CREATE UNIQUE INDEX IF NOT EXISTS uq_interrupt_schedule_active_team
  ON public.interrupt USING btree (schedule_id)
  WHERE team_id IS NOT NULL AND resolved_at IS NULL;

-- Core persisted game-state checks.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_game_stage') THEN
    ALTER TABLE public.game
      ADD CONSTRAINT ck_game_stage CHECK (stage BETWEEN 0 AND 3);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_game_positive_limits') THEN
    ALTER TABLE public.game
      ADD CONSTRAINT ck_game_positive_limits CHECK (max_songs > 0 AND max_albums > 0);
  END IF;
END $$;
