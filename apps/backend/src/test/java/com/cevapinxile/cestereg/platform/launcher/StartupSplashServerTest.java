package com.cevapinxile.cestereg.platform.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartupSplashServerTest {

  private String originalSplashProp;
  private String originalBrowserProp;
  private String originalPortProp;
  private String originalGraceProp;

  @BeforeEach
  void setUp() {
    originalSplashProp = System.getProperty("splash.enabled");
    originalBrowserProp = System.getProperty("splash.browser.enabled");
    originalPortProp = System.getProperty("server.port");
    originalGraceProp = System.getProperty("splash.cleanup.grace.ms");

    // Ensure tests do not attempt to open external browser windows
    System.setProperty("splash.browser.enabled", "false");
    System.setProperty("splash.cleanup.grace.ms", "200");
  }

  @AfterEach
  void tearDown() {
    restoreProperty("splash.enabled", originalSplashProp);
    restoreProperty("splash.browser.enabled", originalBrowserProp);
    restoreProperty("server.port", originalPortProp);
    restoreProperty("splash.cleanup.grace.ms", originalGraceProp);
  }

  private void restoreProperty(final String key, final String value) {
    if (value != null) {
      System.setProperty(key, value);
    } else {
      System.clearProperty(key);
    }
  }

  @Test
  void resolvesPortCorrectlyFromArgsAndProperties() {
    assertEquals(8080, StartupSplashServer.resolveTargetPort(new String[] {}));
    assertEquals(9090, StartupSplashServer.resolveTargetPort(new String[] {"--server.port=9090"}));
    assertEquals(
        9091, StartupSplashServer.resolveTargetPort(new String[] {"--server.port", "9091"}));

    System.setProperty("server.port", "9092");
    assertEquals(9092, StartupSplashServer.resolveTargetPort(new String[] {}));
  }

  @Test
  void resolvesContextPathCorrectly() {
    assertEquals("", StartupSplashServer.resolveContextPath(new String[] {}));
    assertEquals(
        "/quiz",
        StartupSplashServer.resolveContextPath(
            new String[] {"--server.servlet.context-path=/quiz"}));
    assertEquals(
        "/app",
        StartupSplashServer.resolveContextPath(
            new String[] {"--server.servlet.context-path", "app/"}));
  }

  @Test
  void detectsWhenSplashIsDisabled() {
    assertTrue(StartupSplashServer.isSplashEnabled(new String[] {}));
    assertFalse(StartupSplashServer.isSplashEnabled(new String[] {"--splash.enabled=false"}));

    System.setProperty("splash.enabled", "false");
    assertFalse(StartupSplashServer.isSplashEnabled(new String[] {}));

    final StartupSplashServer server =
        StartupSplashServer.start(new String[] {"--splash.enabled=false"});
    assertNull(server);
  }

  @Test
  void ephemeralServerServesHtmlAndTracksReadyLifecycle() throws Exception {
    final int reservedPort = findAvailablePort();
    final String[] args = new String[] {"--server.port=" + reservedPort};

    final StartupSplashServer splash = StartupSplashServer.start(args);
    assertNotNull(splash);
    assertFalse(splash.isAlreadyRunning());
    assertTrue(splash.getEphemeralPort() > 0);
    assertEquals(StartupSplashServer.SplashStatus.STARTING, splash.getStatus());

    final HttpClient client = HttpClient.newHttpClient();

    // 1) Test root HTML endpoint
    final HttpRequest rootReq =
        HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + splash.getEphemeralPort() + "/"))
            .GET()
            .build();
    final HttpResponse<String> rootRes = client.send(rootReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, rootRes.statusCode());
    assertTrue(rootRes.headers().firstValue("Content-Type").orElse("").contains("text/html"));
    assertTrue(rootRes.body().contains("RHYTHMIC RIDDLES"));

    // 2) Test initial status endpoint
    final HttpRequest statusReq =
        HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + splash.getEphemeralPort() + "/status"))
            .GET()
            .build();
    final HttpResponse<String> statusRes =
        client.send(statusReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, statusRes.statusCode());
    assertTrue(
        statusRes.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    assertTrue(statusRes.body().contains("\"status\":\"STARTING\""));
    assertTrue(statusRes.body().contains("http://localhost:" + reservedPort + "/"));

    // 3) Mark ready
    splash.markReady();
    assertEquals(StartupSplashServer.SplashStatus.READY, splash.getStatus());

    final HttpResponse<String> readyRes =
        client.send(statusReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, readyRes.statusCode());
    assertTrue(readyRes.body().contains("\"status\":\"READY\""));

    // Clean up
    splash.stop();
  }

  @Test
  void marksFailedAndExposesErrorMessage() throws Exception {
    final int reservedPort = findAvailablePort();
    final StartupSplashServer splash =
        StartupSplashServer.start(new String[] {"--server.port=" + reservedPort});
    assertNotNull(splash);

    splash.markFailed("Database connection refused on port 5432");
    assertEquals(StartupSplashServer.SplashStatus.FAILED, splash.getStatus());

    final HttpClient client = HttpClient.newHttpClient();
    final HttpRequest statusReq =
        HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + splash.getEphemeralPort() + "/status"))
            .GET()
            .build();
    final HttpResponse<String> statusRes =
        client.send(statusReq, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, statusRes.statusCode());
    assertTrue(statusRes.body().contains("\"status\":\"FAILED\""));
    assertTrue(statusRes.body().contains("Database connection refused on port 5432"));

    splash.stop();
  }

  @Test
  void detectsSingleInstanceWhenPortAlreadyOpen() throws Exception {
    final int port = findAvailablePort();

    // Start a listener on the port to simulate another running instance
    try (final ServerSocket socket = new ServerSocket(port)) {
      assertTrue(StartupSplashServer.isPortOpen("127.0.0.1", port, 300));

      final StartupSplashServer splash =
          StartupSplashServer.start(new String[] {"--server.port=" + port});
      assertNotNull(splash);
      assertTrue(splash.isAlreadyRunning());
    }

    // Now port is released
    assertFalse(StartupSplashServer.isPortOpen("127.0.0.1", port, 300));
  }

  @Test
  void extractsUserFriendlyErrors() {
    final Exception bindEx = new java.net.BindException("Address already in use");
    final String bindMsg = StartupSplashServer.extractUserFriendlyError(bindEx);
    assertTrue(bindMsg.contains("already in use"));

    final Exception dbEx =
        new RuntimeException(
            "Unable to connect to database host", new IOException("Connection refused"));
    final String dbMsg = StartupSplashServer.extractUserFriendlyError(dbEx);
    assertTrue(dbMsg.contains("Database connection failed"));

    final Exception genericEx = new IllegalStateException("Missing required configuration");
    final String genericMsg = StartupSplashServer.extractUserFriendlyError(genericEx);
    assertTrue(genericMsg.contains("Missing required configuration"));
  }

  private int findAvailablePort() throws IOException {
    try (final ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
