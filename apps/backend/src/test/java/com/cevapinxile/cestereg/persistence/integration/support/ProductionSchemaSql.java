package com.cevapinxile.cestereg.persistence.integration.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Locates and reads production PostgreSQL scripts used by persistence integration tests. */
public final class ProductionSchemaSql {

  private static final String SCHEMA_FILE = "db_01_create_schema.sql";

  private ProductionSchemaSql() {}

  public static String read() {
    return read(SCHEMA_FILE);
  }

  public static String read(final String fileName) {
    try {
      return Files.readString(find(fileName), StandardCharsets.UTF_8);
    } catch (final IOException exception) {
      throw new IllegalStateException("Could not read db/" + fileName, exception);
    }
  }

  public static Path find() {
    return find(SCHEMA_FILE);
  }

  public static Path find(final String fileName) {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

    while (current != null) {
      final Path candidate = current.resolve("db").resolve(fileName);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }

    throw new IllegalStateException(
        "Could not locate db/" + fileName + " from the current working directory");
  }
}
