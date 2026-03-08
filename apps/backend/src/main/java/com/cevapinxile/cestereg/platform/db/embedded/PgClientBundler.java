/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.platform.db.embedded;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures a bundled PostgreSQL client (psql) is extracted from application resources into a
 * writable directory, and returns the path to the extracted psql binary.
 *
 * <p>This is meant for "ship everything locally" deployments where the app needs to execute psql
 * for DB scripts/migrations without requiring system-level postgres tools.
 */
public final class PgClientBundler {

  private static final Logger LOG = LoggerFactory.getLogger(PgClientBundler.class);

  /**
   * Major version of the bundled PostgreSQL client. Must match the directory layout inside the
   * packaged tarball.
   */
  private static final String VERSION = "18";

  private PgClientBundler() {}

  /**
   * Ensures the bundled PostgreSQL client is installed and returns the {@code psql} binary path.
   *
   * <p>If the client for the current platform and {@link #VERSION} is not yet installed under
   * {@code appBaseDir/pg-client/...}, the bundled {@code pg-client.tgz} resource is extracted.
   * Installation is considered complete when a marker file and an executable {@code psql} binary
   * are present.
   *
   * @param appBaseDir writable base directory used to install the bundled client
   * @return path to the installed {@code psql} executable
   * @throws IOException if extraction or installation fails
   * @throws FileNotFoundException if pg-client.tgz isn't provided
   */
  public static Path ensureBundledPsqlInstalled(final Path appBaseDir) throws IOException {

    final String platform = platformKey();

    final Path installRoot =
        appBaseDir.resolve("pg-client").resolve(platform).resolve("v" + VERSION);

    // Marker file helps avoid re-extracting on every startup.
    final Path marker = installRoot.resolve(".installed");

    // Expected psql location *inside* the extracted archive tree.
    final String exe = platform.startsWith("windows") ? "psql.exe" : "psql";

    final Path psql =
        installRoot
            .resolve("pg-client-bundle")
            .resolve("postgresql-" + VERSION)
            .resolve("bin")
            .resolve(exe);

    // Fast-path: if we already installed and the binary is executable, just reuse it.
    if (Files.exists(marker) && Files.isExecutable(psql)) {
      LOG.debug("Bundled psql already installed: {}", psql);
      return psql;
    }

    Files.createDirectories(installRoot);

    LOG.info("Installing bundled pg-client v{} into {}", VERSION, installRoot);

    // Extract tar.gz from resources. This file must be packaged under:
    //   src/main/resources/pg-client/___-x86_64/pg-client.tgz
    final String resource = "/pg-client/" + platform + "/pg-client.tgz";

    try (InputStream in = PgClientBundler.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new FileNotFoundException("Missing pg-client.tgz in resources at " + resource);
      }
      extractTarGz(in, installRoot);
    }

    // Ensure psql is executable (owner executable; not necessarily global).
    if (!platform.startsWith("windows")) {
      psql.toFile().setExecutable(true, false);
    }

    // Marker indicates installation finished successfully.
    Files.writeString(marker, "ok");

    LOG.info("Bundled psql installed at {}", psql);

    return psql;
  }

  private static String platformKey() {
    final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    final String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

    final boolean x64 = arch.contains("amd64") || arch.contains("x86_64");

    if (os.contains("win")) {
      if (!x64) {
        throw new IllegalStateException(
            "Bundled pg client only supports Windows x64. Detected: " + os + " " + arch);
      }
      return "windows-x86_64"; // <-- change to your chosen folder name (see suggestions below)
    }

    if (os.contains("linux")) {
      if (!x64) {
        throw new IllegalStateException(
            "Bundled pg client only supports Linux x64. Detected: " + os + " " + arch);
      }
      return "linux-x86_64";
    }

    if (os.contains("mac")) {
      final boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
      if (arm64) {
        return "macos-arm64";
      }
      if (x64) {
        return "macos-x86_64";
      }
      throw new IllegalStateException(
          "Bundled pg client only supports macOS x86_64 or arm64. Detected: " + os + " " + arch);
    }

    throw new IllegalStateException("Unsupported OS for bundled pg client: " + os + " " + arch);
  }

  /**
   * Minimal tar.gz extractor: - Reads TAR headers (512-byte blocks) from a gzip stream - Writes
   * files/directories into targetDir - Blocks path traversal attempts (e.g., ../../etc/passwd)
   *
   * <p>NOTE: This does not validate checksums or all TAR variants; it's "good enough" for
   * extracting a known, controlled bundle shipped with the app.
   */
  private static void extractTarGz(final InputStream input, final Path targetDir)
      throws IOException {
    try (GZIPInputStream gzip = new GZIPInputStream(input)) {

      final byte[] header = new byte[512];

      while (true) {

        // TAR format: each entry begins with a 512-byte header block.
        final int read = gzip.readNBytes(header, 0, 512);
        if (read < 512) {
          break;
        }

        // Two consecutive empty blocks indicate end of archive; we stop at first empty header.
        boolean empty = true;
        for (byte b : header) {
          if (b != 0) {
            empty = false;
            break;
          }
        }
        if (empty) {
          break;
        }

        // File name is stored at bytes 0..99, null-terminated.
        final String name = new String(header, 0, 100).trim().replace("\0", "");
        if (name.isBlank()) {
          break;
        }

        // File size is stored as octal text at bytes 124..135.
        final String sizeOctal = new String(header, 124, 12).trim().replace("\0", "");
        final long size = sizeOctal.isBlank() ? 0 : Long.parseLong(sizeOctal, 8);

        // Type flag: '5' = directory, '0' or '\0' = file (others ignored here).
        final char type = (char) header[156];

        final Path outPath = targetDir.resolve(name).normalize();

        // Security: prevent TAR path traversal (e.g., entries like "../../something").
        if (!outPath.startsWith(targetDir)) {
          throw new IOException("Blocked tar path traversal: " + name);
        }

        if (type == '5') {
          Files.createDirectories(outPath);
        } else {
          Files.createDirectories(outPath.getParent());

          try (OutputStream out =
              Files.newOutputStream(
                  outPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // Copy exactly <size> bytes from the stream for this file entry.
            copyExact(gzip, out, size);
          }
        }

        // TAR entries are padded to 512-byte boundaries.
        final long padding = (512 - (size % 512)) % 512;
        if (padding > 0) {
          gzip.skipNBytes(padding);
        }
      }
    }
  }

  /**
   * Copies an exact number of bytes from in -> out, throwing if the stream ends early. This avoids
   * accidentally reading into the next TAR header.
   */
  private static void copyExact(final InputStream in, final OutputStream out, final long bytes)
      throws IOException {
    final byte[] buffer = new byte[8192];
    long remaining = bytes;

    while (remaining > 0) {
      final int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read == -1) {
        throw new EOFException("Unexpected EOF in tar stream");
      }
      out.write(buffer, 0, read);
      remaining -= read;
    }
  }
}
