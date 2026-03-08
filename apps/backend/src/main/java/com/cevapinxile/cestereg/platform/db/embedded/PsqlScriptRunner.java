package com.cevapinxile.cestereg.platform.db.embedded;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public final class PsqlScriptRunner {

  private static final Logger LOG = LoggerFactory.getLogger(PsqlScriptRunner.class);

  private PsqlScriptRunner() {}

  /**
   * Executes all SQL scripts located under {@code classpath:db/*.sql} exactly once.
   *
   * <p>The method checks for a marker file {@code .cestereg_sql_done} inside {@code dataDir}. If
   * the marker exists, execution is skipped to ensure the bootstrap runs only once across
   * application restarts.
   *
   * <p>SQL scripts are first extracted from the classpath into a temporary directory, because
   * {@code psql} requires filesystem paths.
   *
   * <p>The bundled {@code psql} client is installed (if necessary) and used to execute the scripts
   * sequentially:
   *
   * <ul>
   *   <li>The first script is executed in an <b>admin phase</b>, connecting as user {@code
   *       postgres} to the {@code postgres} database. This is intended for tasks such as creating
   *       roles, databases, or extensions.
   *   <li>All remaining scripts are executed in an <b>application phase</b>, connecting with the
   *       application credentials ({@code props.getUsername()}, {@code props.getDatabase()}).
   * </ul>
   *
   * <p>If all scripts complete successfully, the marker file is written to {@code dataDir} to
   * prevent subsequent executions.
   *
   * @param props database connection configuration
   * @param actualPort the port on which the embedded PostgreSQL instance is running
   * @param distBase base distribution directory (reserved for future use)
   * @param dataDir application data directory where the execution marker is stored
   * @throws Exception if script extraction or SQL execution fails
   */
  public static void runOnceFromClasspathDbFolder(
      final AppEmbeddedDbProperties props,
      final int actualPort,
      final Path distBase,
      final Path dataDir)
      throws Exception {

    final Path marker = dataDir.resolve(".cestereg_sql_done");
    if (Files.exists(marker)) {
      LOG.debug("SQL bootstrap already done (marker exists): {}", marker);
      return;
    }

    // Extract scripts to a TEMP directory (not APPDATA), so we don't pollute user data
    final Path tempSqlDir = Files.createTempDirectory("cestereg-sql-");
    tempSqlDir.toFile().deleteOnExit(); // best-effort cleanup

    final List<Path> scripts = extractClasspathScriptsTo(tempSqlDir);

    // Ensure bundled psql is installed and executable
    final Path psql = PgClientBundler.ensureBundledPsqlInstalled(AppDataDirs.appDataBaseDir());
    psql.toFile().setExecutable(true, false);

    LOG.info(
        "Running {} SQL script(s) via bundled psql (host={}, port={}) from {}",
        scripts.size(),
        props.getHost(),
        actualPort,
        tempSqlDir);

    for (int i = 0; i < scripts.size(); i++) {
      final Path script = scripts.get(i);
      final long start = System.nanoTime();

      if (i == 0) {
        // Admin phase: connect as postgres user to db "postgres" (e.g., create user/db, extensions)
        LOG.info("SQL admin phase: executing {}", script.getFileName());
        runPsql(
            psql, tempSqlDir, props.getHost(), actualPort, "postgres", "postgres", null, script);
      } else {
        // App phase: connect as app user to target DB (schema/data init)
        LOG.info("SQL app phase: executing {}", script.getFileName());
        runPsql(
            psql,
            tempSqlDir,
            props.getHost(),
            actualPort,
            props.getUsername(),
            props.getDatabase(),
            props.getPassword(),
            script);
      }

      final long ms = (System.nanoTime() - start) / 1_000_000;
      LOG.info("Finished {} in {} ms", script.getFileName(), ms);
    }

    Files.writeString(marker, "ok", StandardCharsets.UTF_8);
    LOG.info("SQL bootstrap completed; wrote marker {}", marker);
  }

  /**
   * Copies classpath SQL scripts (db/*.sql) to an output directory and returns them in filename
   * order. Sorting makes execution deterministic.
   */
  private static List<Path> extractClasspathScriptsTo(final Path outDir) throws IOException {
    final Resource[] resources =
        new PathMatchingResourcePatternResolver().getResources("classpath:db/*.sql");

    Arrays.sort(
        resources,
        Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));

    final List<Path> result = new ArrayList<>();
    for (Resource r : resources) {
      if (!r.exists() || r.getFilename() == null) {
        continue;
      }

      final Path target = outDir.resolve(r.getFilename());
      try (var in = r.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      result.add(target);
    }

    if (result.isEmpty()) {
      throw new IllegalStateException("No SQL scripts found under classpath:db/*.sql");
    }

    LOG.debug("Extracted SQL scripts to {}: {}", outDir, result);
    return result;
  }

  /**
   * Executes one SQL script using psql.
   *
   * <p>Notes: - ON_ERROR_STOP=1: any SQL error stops execution and causes non-zero exit - ECHO=all
   * / -e: echoes commands; useful for debugging, but can be noisy - PGPASSWORD is only set if
   * provided (keeps env clean) - LD_LIBRARY_PATH is set to the bundled Postgres lib dir so psql can
   * run from the bundle
   */
  private static void runPsql(
      final Path psql,
      final Path workingDir,
      final String host,
      final int port,
      final String user,
      final String db,
      final String passwordOrNull,
      final Path script)
      throws Exception {

    final List<String> cmd = new ArrayList<>();
    cmd.add(psql.toAbsolutePath().toString());
    cmd.add("-v");
    cmd.add("ON_ERROR_STOP=1");
    cmd.add("-v");
    cmd.add("ECHO=all");
    cmd.add("-e");
    cmd.add("-h");
    cmd.add(host);
    cmd.add("-p");
    cmd.add(String.valueOf(port));
    cmd.add("-U");
    cmd.add(user);
    cmd.add("-d");
    cmd.add(db);
    cmd.add("-f");
    cmd.add(script.toAbsolutePath().toString());

    // psql binary lives in .../bin; libs are typically in .../lib
    final Path binDir = psql.getParent();
    final Path pgRoot = binDir.getParent();
    final Path libDir = pgRoot.resolve("lib");

    final ProcessBuilder pb =
        new ProcessBuilder(cmd).directory(workingDir.toFile()).redirectErrorStream(true);

    final Map<String, String> env = pb.environment();

    // Ensure our bundled psql is found first + has its own libs
    final String sep = java.io.File.pathSeparator; // ";" on Windows, ":" on Linux/macOS

    // Always prepend binDir to PATH (psql + required dll/so/dylib resolution)
    env.put("PATH", binDir.toAbsolutePath() + sep + env.getOrDefault("PATH", ""));

    final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      // Windows loads DLLs from PATH; include lib as well just in case
      env.put("PATH", libDir.toAbsolutePath() + sep + env.getOrDefault("PATH", ""));
    } else if (os.contains("mac")) {
      env.put(
          "DYLD_LIBRARY_PATH",
          libDir.toAbsolutePath() + sep + env.getOrDefault("DYLD_LIBRARY_PATH", ""));
    } else {
      env.put(
          "LD_LIBRARY_PATH",
          libDir.toAbsolutePath() + sep + env.getOrDefault("LD_LIBRARY_PATH", ""));
    }

    if (passwordOrNull != null && !passwordOrNull.isBlank()) {
      env.put("PGPASSWORD", passwordOrNull);
    }

    // Don't log full command with credentials; this one doesn't include password (it’s in env).
    LOG.debug(
        "Executing psql for script {} (user={}, db={}, host={}, port={})",
        script.getFileName(),
        user,
        db,
        host,
        port);

    final Process p = pb.start();
    final String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final int exit = p.waitFor();

    if (exit != 0) {
      // Avoid throwing megabytes (or sensitive logs) in exception; keep tail.
      final String tail =
          output.length() <= 4000 ? output : output.substring(output.length() - 4000);
      LOG.debug("psql output (full) for {}:\n{}", script.getFileName(), output);

      throw new IllegalStateException(
          "psql failed on script "
              + script.getFileName()
              + " (exit="
              + exit
              + ")\n--- output tail ---\n"
              + tail);
    }

    LOG.debug("psql succeeded for {}", script.getFileName());
  }
}
