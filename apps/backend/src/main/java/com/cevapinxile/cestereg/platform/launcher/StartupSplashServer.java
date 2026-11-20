package com.cevapinxile.cestereg.platform.launcher;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pre-Spring ephemeral HTTP server that presents a splash screen in the user's browser immediately
 * upon application launch and redirects once Spring Boot is ready.
 */
public final class StartupSplashServer {

  /** Lifecycle status of the splash screen. */
  public enum SplashStatus {
    /** Application is starting up. */
    STARTING,
    /** Application has finished starting up and is ready to accept traffic. */
    READY,
    /** Application failed to start up. */
    FAILED
  }

  private static final int DEFAULT_TARGET_PORT = 8080;
  private static final int SOCKET_PROBE_TIMEOUT_MS = 350;
  private static final long DEFAULT_CLEANUP_GRACE_MS = 3500L;
  private static final long DEFAULT_FAILURE_WAIT_MS = 10000L;

  private final HttpServer server;
  private final int ephemeralPort;
  private final ScheduledExecutorService cleanupScheduler;
  private final ExecutorService workerExecutor;
  private final boolean alreadyRunning;
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private volatile SplashStatus status = SplashStatus.STARTING;
  private volatile String targetUrl;
  private volatile String errorMessage;

  private StartupSplashServer(
      final HttpServer server,
      final int ephemeralPort,
      final String targetUrl,
      final boolean alreadyRunning) {
    this.server = server;
    this.ephemeralPort = ephemeralPort;
    this.targetUrl = targetUrl;
    this.alreadyRunning = alreadyRunning;

    if (server != null) {
      this.cleanupScheduler =
          Executors.newSingleThreadScheduledExecutor(
              runnable -> {
                final Thread thread = new Thread(runnable, "splash-cleanup");
                thread.setDaemon(true);
                return thread;
              });

      this.workerExecutor =
          Executors.newCachedThreadPool(
              runnable -> {
                final Thread thread = new Thread(runnable, "splash-http");
                thread.setDaemon(true);
                return thread;
              });
      server.setExecutor(this.workerExecutor);
    } else {
      this.cleanupScheduler = null;
      this.workerExecutor = null;
    }
  }

  /**
   * Checks whether the startup splash server is enabled.
   *
   * @param args command-line arguments.
   * @return true if enabled, false otherwise.
   */
  public static boolean isSplashEnabled(final String[] args) {
    if (args != null) {
      for (final String arg : args) {
        if ("--splash.enabled=false".equalsIgnoreCase(arg)) {
          return false;
        }
      }
    }

    final String sysProp = System.getProperty("splash.enabled");
    if ("false".equalsIgnoreCase(sysProp)) {
      return false;
    }

    final String env = System.getenv("SPLASH_ENABLED");
    if ("false".equalsIgnoreCase(env)) {
      return false;
    }

    return true;
  }

  /**
   * Resolves the target port that Spring Boot will bind to, considering args and environment.
   *
   * @param args command line arguments.
   * @return resolved port number.
   */
  public static int resolveTargetPort(final String[] args) {
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        final String arg = args[i];
        if (arg.startsWith("--server.port=")) {
          try {
            return Integer.parseInt(arg.substring("--server.port=".length()).trim());
          } catch (final NumberFormatException ignored) {
            // fallback
          }
        } else if ("--server.port".equals(arg) && i + 1 < args.length) {
          try {
            return Integer.parseInt(args[i + 1].trim());
          } catch (final NumberFormatException ignored) {
            // fallback
          }
        }
      }
    }

    final String sysProp = System.getProperty("server.port");
    if (sysProp != null && !sysProp.isBlank()) {
      try {
        return Integer.parseInt(sysProp.trim());
      } catch (final NumberFormatException ignored) {
        // fallback
      }
    }

    final String envPort = System.getenv("SERVER_PORT");
    if (envPort != null && !envPort.isBlank()) {
      try {
        return Integer.parseInt(envPort.trim());
      } catch (final NumberFormatException ignored) {
        // fallback
      }
    }

    return DEFAULT_TARGET_PORT;
  }

  /**
   * Resolves the context path configured for the server.
   *
   * @param args command line arguments.
   * @return resolved context path or empty string.
   */
  public static String resolveContextPath(final String[] args) {
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        final String arg = args[i];
        if (arg.startsWith("--server.servlet.context-path=")) {
          return sanitizeContextPath(
              arg.substring("--server.servlet.context-path=".length()).trim());
        } else if ("--server.servlet.context-path".equals(arg) && i + 1 < args.length) {
          return sanitizeContextPath(args[i + 1].trim());
        }
      }
    }

    final String sysProp = System.getProperty("server.servlet.context-path");
    if (sysProp != null && !sysProp.isBlank()) {
      return sanitizeContextPath(sysProp.trim());
    }

    final String envPath = System.getenv("SERVER_SERVLET_CONTEXT_PATH");
    if (envPath != null && !envPath.isBlank()) {
      return sanitizeContextPath(envPath.trim());
    }

    return "";
  }

  private static String sanitizeContextPath(final String path) {
    if (path == null || path.isBlank() || "/".equals(path.trim())) {
      return "";
    }
    String result = path.trim();
    if (!result.startsWith("/")) {
      result = "/" + result;
    }
    if (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  /**
   * Checks whether the target TCP port is currently accepting connections.
   *
   * @param host host address.
   * @param port port number.
   * @param timeoutMs connection timeout in milliseconds.
   * @return true if port connection succeeded, false otherwise.
   */
  public static boolean isPortOpen(final String host, final int port, final int timeoutMs) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeoutMs);
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  /**
   * Initializes and starts the startup splash screen server if enabled.
   *
   * @param args command line arguments.
   * @return active StartupSplashServer instance, sentinel instance if already running, or null if
   *     disabled.
   */
  public static StartupSplashServer start(final String[] args) {
    if (!isSplashEnabled(args)) {
      return null;
    }

    final int targetPort = resolveTargetPort(args);
    final String contextPath = resolveContextPath(args);
    final String targetUrl = "http://localhost:" + targetPort + contextPath + "/";

    // 1) Single-instance / already-running check
    if (isPortOpen("127.0.0.1", targetPort, SOCKET_PROBE_TIMEOUT_MS)) {
      PlatformBrowserLauncher.openBrowser(targetUrl);
      System.out.println(
          "[Splash] Target port "
              + targetPort
              + " is already active. Opened running application in browser.");
      return new StartupSplashServer(null, -1, targetUrl, true);
    }

    // 2) Start ephemeral HttpServer on port 0
    try {
      final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      final int ephemeralPort = server.getAddress().getPort();
      final StartupSplashServer splash =
          new StartupSplashServer(server, ephemeralPort, targetUrl, false);

      final String splashHtml = loadSplashHtml();

      server.createContext(
          "/",
          new HttpHandler() {
            @Override
            public void handle(final HttpExchange exchange) throws IOException {
              final String path = exchange.getRequestURI().getPath();
              if ("/favicon.ico".equals(path)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
              }

              final byte[] response = splashHtml.getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
              exchange
                  .getResponseHeaders()
                  .set("Cache-Control", "no-cache, no-store, must-revalidate");
              exchange.sendResponseHeaders(200, response.length);
              try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
              }
              exchange.close();
            }
          });

      server.createContext(
          "/status",
          new HttpHandler() {
            @Override
            public void handle(final HttpExchange exchange) throws IOException {
              final byte[] response = splash.renderStatusJson().getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
              exchange
                  .getResponseHeaders()
                  .set("Cache-Control", "no-cache, no-store, must-revalidate");
              exchange.sendResponseHeaders(200, response.length);
              try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
              }
              exchange.close();
            }
          });

      server.start();

      final String splashUrl = "http://localhost:" + ephemeralPort + "/";
      System.out.println(
          "[Splash] Ephemeral splash server running at " + splashUrl + " -> target " + targetUrl);

      // 3) Launch browser immediately to ephemeral server
      PlatformBrowserLauncher.openBrowser(splashUrl);

      return splash;
    } catch (final IOException e) {
      System.err.println(
          "[Splash] Warning: Failed to start ephemeral splash server: " + e.getMessage());
      return null;
    }
  }

  /** Marks the server status as READY and schedules server stop after a short grace period. */
  public void markReady() {
    markReady(this.targetUrl);
  }

  /**
   * Marks the server status as READY with a specific target URL and schedules cleanup.
   *
   * @param finalTargetUrl the final resolved target URL.
   */
  public synchronized void markReady(final String finalTargetUrl) {
    if (this.status != SplashStatus.STARTING) {
      return;
    }

    if (finalTargetUrl != null && !finalTargetUrl.isBlank()) {
      this.targetUrl = finalTargetUrl;
    }

    this.status = SplashStatus.READY;
    System.out.println(
        "[Splash] Application is READY. Notifying splash screen to redirect to " + this.targetUrl);

    if (this.cleanupScheduler != null) {
      final long gracePeriodMs = Long.getLong("splash.cleanup.grace.ms", DEFAULT_CLEANUP_GRACE_MS);
      this.cleanupScheduler.schedule(this::stop, gracePeriodMs, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Marks the server status as FAILED with an error message.
   *
   * @param error concise, user-friendly error message.
   */
  public synchronized void markFailed(final String error) {
    this.status = SplashStatus.FAILED;
    this.errorMessage =
        (error != null && !error.isBlank()) ? error : "Application failed to start.";
    System.err.println("[Splash] Startup FAILED: " + this.errorMessage);
  }

  /**
   * Handles startup failure by marking state, waiting briefly so the user can read the error, and
   * cleanly shutting down the ephemeral HTTP server.
   *
   * @param throwable cause of startup failure.
   */
  public void handleStartupFailure(final Throwable throwable) {
    final String error = extractUserFriendlyError(throwable);
    markFailed(error);

    final long waitMs = Long.getLong("splash.failure.wait.ms", DEFAULT_FAILURE_WAIT_MS);
    if (waitMs > 0) {
      try {
        Thread.sleep(waitMs);
      } catch (final InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
    stop();
  }

  /** Stops the ephemeral HTTP server and cleans up executors. */
  public synchronized void stop() {
    if (!this.stopped.compareAndSet(false, true)) {
      return;
    }

    if (this.server != null) {
      try {
        this.server.stop(0);
      } catch (final Exception ignored) {
        // cleanup suppression
      }
    }

    if (this.cleanupScheduler != null) {
      this.cleanupScheduler.shutdownNow();
    }

    if (this.workerExecutor != null) {
      this.workerExecutor.shutdownNow();
    }
  }

  /**
   * Returns whether this server represents an already running instance detection.
   *
   * @return true if another instance was already detected on target port, false otherwise.
   */
  public boolean isAlreadyRunning() {
    return this.alreadyRunning;
  }

  /**
   * Returns the current status of the splash screen.
   *
   * @return current SplashStatus.
   */
  public SplashStatus getStatus() {
    return this.status;
  }

  /**
   * Returns the ephemeral port assigned by the OS.
   *
   * @return ephemeral port or -1.
   */
  public int getEphemeralPort() {
    return this.ephemeralPort;
  }

  /**
   * Returns the configured target URL.
   *
   * @return target URL.
   */
  public String getTargetUrl() {
    return this.targetUrl;
  }

  /**
   * Returns the recorded error message if startup failed.
   *
   * @return error message or null.
   */
  public String getErrorMessage() {
    return this.errorMessage;
  }

  /**
   * Renders the current status JSON payload.
   *
   * @return JSON string.
   */
  public String renderStatusJson() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"status\":\"").append(this.status.name()).append("\",");
    sb.append("\"targetUrl\":");
    if (this.targetUrl != null) {
      sb.append("\"").append(escapeJson(this.targetUrl)).append("\"");
    } else {
      sb.append("null");
    }
    sb.append(",\"error\":");
    if (this.errorMessage != null) {
      sb.append("\"").append(escapeJson(this.errorMessage)).append("\"");
    } else {
      sb.append("null");
    }
    sb.append("}");
    return sb.toString();
  }

  private static String escapeJson(final String text) {
    if (text == null) {
      return "";
    }
    final StringBuilder sb = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 32) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  /**
   * Extracts a user-friendly, concise error description from a startup exception chain.
   *
   * @param throwable startup exception.
   * @return human-readable error summary.
   */
  public static String extractUserFriendlyError(final Throwable throwable) {
    if (throwable == null) {
      return "Application encountered an unexpected startup failure.";
    }

    Throwable current = throwable;
    while (current != null) {
      if (current instanceof java.net.BindException
          || current.getClass().getName().contains("PortInUseException")) {
        return "Port is already in use by another application. Please free the port or choose another port.";
      }

      final String msg = current.getMessage();
      if (msg != null && !msg.isBlank()) {
        final String lower = msg.toLowerCase();
        if (lower.contains("database")
            || lower.contains("postgresql")
            || current.getClass().getName().contains("PSQLException")) {
          return "Database connection failed: " + msg;
        }
      }

      if (current.getCause() == current) {
        break;
      }
      current = current.getCause();
    }

    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }

    final String message = root.getMessage();
    if (message != null && !message.isBlank()) {
      return message;
    }

    return root.getClass().getSimpleName() + " occurred during startup.";
  }

  private static String loadSplashHtml() {
    try (InputStream is = StartupSplashServer.class.getResourceAsStream("/splash/splash.html")) {
      if (is != null) {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (final IOException ignored) {
      // fallback to embedded minimal template below
    }

    return """
    <!doctype html>
    <html>
    <head><meta charset="utf-8"><title>Starting...</title></head>
    <body style="background:#100b1b;color:#f2e9ff;font-family:sans-serif;height:100vh;">
      <h2>Starting Rhythmic Riddles...</h2>
      <script>
        setInterval(function() {
          fetch('/status').then(r => r.json()).then(d => {
            if (d.status === 'READY') window.location.replace(d.targetUrl);
          }).catch(()=>{});
        }, 200);
      </script>
    </body>
    </html>
    """;
  }
}
