package com.cevapinxile.cestereg.platform.db.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class PsqlScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(PsqlScriptRunner.class);

    private PsqlScriptRunner() {
    }

    /**
     * Runs all SQL scripts found under classpath:db/*.sql exactly once.
     *
     * The marker file lives in dataDir so reruns are avoided across restarts.
     * Scripts are copied to a temp folder first because psql expects filesystem
     * paths.
     */
    public static void runOnceFromClasspathDbFolder(
            AppEmbeddedDbProperties props,
            int actualPort,
            Path distBase,
            Path dataDir
    ) throws Exception {

        Path marker = dataDir.resolve(".cestereg_sql_done");
        if (Files.exists(marker)) {
            log.debug("SQL bootstrap already done (marker exists): {}", marker);
            return;
        }

        // Extract scripts to a TEMP directory (not APPDATA), so we don't pollute user data
        Path tempSqlDir = Files.createTempDirectory("cestereg-sql-");
        tempSqlDir.toFile().deleteOnExit(); // best-effort cleanup

        List<Path> scripts = extractClasspathScriptsTo(tempSqlDir);

        // Ensure bundled psql is installed and executable
        Path psql = PgClientBundler.ensureBundledPsqlInstalled(AppDataDirs.appDataBaseDir());
        psql.toFile().setExecutable(true, false);

        log.info("Running {} SQL script(s) via bundled psql (host={}, port={}) from {}",
                scripts.size(), props.getHost(), actualPort, tempSqlDir);

        for (int i = 0; i < scripts.size(); i++) {
            Path script = scripts.get(i);
            long start = System.nanoTime();

            if (i == 0) {
                // Admin phase: connect as postgres user to db "postgres" (e.g., create user/db, extensions)
                log.info("SQL admin phase: executing {}", script.getFileName());
                runPsql(psql, tempSqlDir, props.getHost(), actualPort,
                        "postgres", "postgres", null, script);
            } else {
                // App phase: connect as app user to target DB (schema/data init)
                log.info("SQL app phase: executing {}", script.getFileName());
                runPsql(psql, tempSqlDir, props.getHost(), actualPort,
                        props.getUsername(), props.getDatabase(), props.getPassword(), script);
            }

            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("Finished {} in {} ms", script.getFileName(), ms);
        }

        Files.writeString(marker, "ok", StandardCharsets.UTF_8);
        log.info("SQL bootstrap completed; wrote marker {}", marker);
    }

    /**
     * Copies classpath SQL scripts (db/*.sql) to an output directory and
     * returns them in filename order. Sorting makes execution deterministic.
     */
    private static List<Path> extractClasspathScriptsTo(Path outDir) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/*.sql");

        Arrays.sort(resources, Comparator.comparing(
                Resource::getFilename,
                Comparator.nullsLast(String::compareTo)
        ));

        List<Path> result = new ArrayList<>();
        for (Resource r : resources) {
            if (!r.exists() || r.getFilename() == null) {
                continue;
            }

            Path target = outDir.resolve(r.getFilename());
            try (var in = r.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            result.add(target);
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("No SQL scripts found under classpath:db/*.sql");
        }

        log.debug("Extracted SQL scripts to {}: {}", outDir, result);
        return result;
    }

    /**
     * Executes one SQL script using psql.
     *
     * Notes: - ON_ERROR_STOP=1: any SQL error stops execution and causes
     * non-zero exit - ECHO=all / -e: echoes commands; useful for debugging, but
     * can be noisy - PGPASSWORD is only set if provided (keeps env clean) -
     * LD_LIBRARY_PATH is set to the bundled Postgres lib dir so psql can run
     * from the bundle
     */
    private static void runPsql(
            Path psql,
            Path workingDir,
            String host,
            int port,
            String user,
            String db,
            String passwordOrNull,
            Path script
    ) throws Exception {

        List<String> cmd = new ArrayList<>();
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
        Path binDir = psql.getParent();
        Path pgRoot = binDir.getParent();
        Path libDir = pgRoot.resolve("lib");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workingDir.toFile())
                .redirectErrorStream(true);

        var env = pb.environment();

        // Ensure our bundled psql is found first + has its own libs
        String sep = java.io.File.pathSeparator; // ";" on Windows, ":" on Linux/macOS

        // Always prepend binDir to PATH (psql + required dll/so/dylib resolution)
        env.put("PATH", binDir.toAbsolutePath() + sep + env.getOrDefault("PATH", ""));

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // Windows loads DLLs from PATH; include lib as well just in case
            env.put("PATH", libDir.toAbsolutePath() + sep + env.getOrDefault("PATH", ""));
        } else if (os.contains("mac")) {
            env.put("DYLD_LIBRARY_PATH", libDir.toAbsolutePath() + sep + env.getOrDefault("DYLD_LIBRARY_PATH", ""));
        } else {
            env.put("LD_LIBRARY_PATH", libDir.toAbsolutePath() + sep + env.getOrDefault("LD_LIBRARY_PATH", ""));
        }

        if (passwordOrNull != null && !passwordOrNull.isBlank()) {
            env.put("PGPASSWORD", passwordOrNull);
        }

        // Don't log full command with credentials; this one doesn't include password (it’s in env).
        log.debug("Executing psql for script {} (user={}, db={}, host={}, port={})",
                script.getFileName(), user, db, host, port);

        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        if (exit != 0) {
            // Avoid throwing megabytes (or sensitive logs) in exception; keep tail.
            String tail = output.length() <= 4000 ? output : output.substring(output.length() - 4000);
            log.debug("psql output (full) for {}:\n{}", script.getFileName(), output);

            throw new IllegalStateException(
                    "psql failed on script " + script.getFileName()
                    + " (exit=" + exit + ")\n--- output tail ---\n" + tail
            );
        }

        log.debug("psql succeeded for {}", script.getFileName());
    }
}
