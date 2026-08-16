package com.cevapinxile.cestereg.persistence.integration.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Clears the shared ephemeral PostgreSQL database for tests that cannot use test transactions. */
public final class DatabaseTestCleaner {

  private DatabaseTestCleaner() {}

  public static void clear(final JdbcTemplate jdbc) {
    jdbc.execute(
        "TRUNCATE TABLE interrupt, schedule, category, team, game, track, song, album CASCADE");
  }
}
