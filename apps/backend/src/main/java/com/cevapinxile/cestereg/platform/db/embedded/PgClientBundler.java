/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.platform.db.embedded;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures a bundled PostgreSQL client (psql) is extracted from application resources
 * into a writable directory, and returns the path to the extracted psql binary.
 *
 * This is meant for "ship everything locally" deployments where the app needs to
 * execute psql for DB scripts/migrations without requiring system-level postgres tools.
 */
public final class PgClientBundler {

    private static final Logger log = LoggerFactory.getLogger(PgClientBundler.class);

    /**
     * Major version of the bundled PostgreSQL client.
     * Must match the directory layout inside the packaged tarball.
     */
    private static final String VERSION = "18";

    private PgClientBundler() {}

    /**
     * Extracts the bundled pg-client tarball (if not already installed) and returns the psql path.
     *
     * @param appBaseDir base directory the app can write to (e.g. ./data, ~/.appname, etc.)
     * @return path to the extracted psql binary
     */
    public static Path ensureBundledPsqlInstalled(Path appBaseDir) throws IOException {

        // Detect OS/arch and only allow the ones we actually ship a bundle for.
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        if (!os.contains("linux") || !(arch.contains("amd64") || arch.contains("x86_64"))) {
            throw new IllegalStateException(
                    "Bundled pg client only supported for linux x86_64. Detected: " + os + " " + arch
            );
        }

        // Install location:
        //   <appBaseDir>/pg-client/linux-x86_64/v18/...
        // Keeping this deterministic makes upgrades and debugging easier.
        Path installRoot = appBaseDir
                .resolve("pg-client")
                .resolve("linux-x86_64")
                .resolve("v" + VERSION);

        // Marker file helps avoid re-extracting on every startup.
        Path marker = installRoot.resolve(".installed");

        // Expected psql location *inside* the extracted archive tree.
        Path psql = installRoot
                .resolve("pg-client-bundle")
                .resolve("postgresql-" + VERSION)
                .resolve("bin")
                .resolve("psql");

        // Fast-path: if we already installed and the binary is executable, just reuse it.
        if (Files.exists(marker) && Files.isExecutable(psql)) {
            log.debug("Bundled psql already installed: {}", psql);
            return psql;
        }

        Files.createDirectories(installRoot);

        log.info("Installing bundled pg-client v{} into {}", VERSION, installRoot);

        // Extract tar.gz from resources. This file must be packaged under:
        //   src/main/resources/pg-client/linux-x86_64/pg-client.tgz
        try (InputStream in = PgClientBundler.class
                .getResourceAsStream("/pg-client/linux-x86_64/pg-client.tgz")) {

            if (in == null) {
                throw new FileNotFoundException("Missing pg-client.tgz in resources");
            }

            extractTarGz(in, installRoot);
        }

        // Ensure psql is executable (owner executable; not necessarily global).
        psql.toFile().setExecutable(true, false);

        // Marker indicates installation finished successfully.
        Files.writeString(marker, "ok");

        log.info("Bundled psql installed at {}", psql);

        return psql;
    }

    /**
     * Minimal tar.gz extractor:
     * - Reads TAR headers (512-byte blocks) from a gzip stream
     * - Writes files/directories into targetDir
     * - Blocks path traversal attempts (e.g., ../../etc/passwd)
     *
     * NOTE: This does not validate checksums or all TAR variants; it's "good enough"
     * for extracting a known, controlled bundle shipped with the app.
     */
    private static void extractTarGz(InputStream input, Path targetDir) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(input)) {

            byte[] header = new byte[512];

            while (true) {

                // TAR format: each entry begins with a 512-byte header block.
                int read = gzip.readNBytes(header, 0, 512);
                if (read < 512) break;

                // Two consecutive empty blocks indicate end of archive; we stop at first empty header.
                boolean empty = true;
                for (byte b : header) {
                    if (b != 0) { empty = false; break; }
                }
                if (empty) break;

                // File name is stored at bytes 0..99, null-terminated.
                String name = new String(header, 0, 100).trim().replace("\0", "");
                if (name.isBlank()) break;

                // File size is stored as octal text at bytes 124..135.
                String sizeOctal = new String(header, 124, 12).trim().replace("\0", "");
                long size = sizeOctal.isBlank() ? 0 : Long.parseLong(sizeOctal, 8);

                // Type flag: '5' = directory, '0' or '\0' = file (others ignored here).
                char type = (char) header[156];

                Path outPath = targetDir.resolve(name).normalize();

                // Security: prevent TAR path traversal (e.g., entries like "../../something").
                if (!outPath.startsWith(targetDir)) {
                    throw new IOException("Blocked tar path traversal: " + name);
                }

                if (type == '5') {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());

                    try (OutputStream out = Files.newOutputStream(
                            outPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {

                        // Copy exactly <size> bytes from the stream for this file entry.
                        copyExact(gzip, out, size);
                    }
                }

                // TAR entries are padded to 512-byte boundaries.
                long padding = (512 - (size % 512)) % 512;
                if (padding > 0) {
                    gzip.skipNBytes(padding);
                }
            }
        }
    }

    /**
     * Copies an exact number of bytes from in -> out, throwing if the stream ends early.
     * This avoids accidentally reading into the next TAR header.
     */
    private static void copyExact(InputStream in, OutputStream out, long bytes) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = bytes;

        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new EOFException("Unexpected EOF in tar stream");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }
}