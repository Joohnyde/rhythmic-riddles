package com.cevapinxile.cestereg.persistence.integration.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Shared ephemeral PostgreSQL process for persistence integration tests. */
public final class EmbeddedPostgresTestDatabase {

  private static final List<String> INITIALIZER_SCRIPTS =
      List.of("db_01_create_schema.sql", "db_04_add_runtime_invariants.sql");

  private static final EmbeddedPostgres POSTGRES = startAndInitialize();

  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    POSTGRES.close();
                  } catch (final IOException ignored) {
                    // JVM shutdown: there is no useful recovery action here.
                  }
                },
                "db-integration-postgres-shutdown"));
  }

  private EmbeddedPostgresTestDatabase() {}

  public static String jdbcUrl() {
    return "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres";
  }

  public static List<String> initializerScripts() {
    return INITIALIZER_SCRIPTS;
  }

  private static EmbeddedPostgres startAndInitialize() {
    try {
      final EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
      initializeProductionSchema(postgres);
      return postgres;
    } catch (final IOException | SQLException exception) {
      throw new IllegalStateException(
          "Could not start or initialize PostgreSQL for DB integration tests. "
              + "Run Maven with -Dplatform=linux, -Dplatform=windows, or -Dplatform=macos.",
          exception);
    }
  }

  private static void initializeProductionSchema(final EmbeddedPostgres postgres)
      throws SQLException {
    try (final Connection connection = postgres.getPostgresDatabase().getConnection();
        final Statement statement = connection.createStatement()) {
      for (final String script : INITIALIZER_SCRIPTS) {
        statement.execute(ProductionSchemaSql.read(script));
      }
    }
  }
}
