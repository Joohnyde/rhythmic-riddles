package com.cevapinxile.cestereg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RhytmicRiddlesApplicationStartupTest {

  private String originalBrowserProp;
  private String originalFailureWaitProp;

  @BeforeEach
  void setUp() {
    originalBrowserProp = System.getProperty("splash.browser.enabled");
    originalFailureWaitProp = System.getProperty("splash.failure.wait.ms");

    System.setProperty("splash.browser.enabled", "false");
    System.setProperty("splash.failure.wait.ms", "50");
  }

  @AfterEach
  void tearDown() {
    if (originalBrowserProp != null) {
      System.setProperty("splash.browser.enabled", originalBrowserProp);
    } else {
      System.clearProperty("splash.browser.enabled");
    }

    if (originalFailureWaitProp != null) {
      System.setProperty("splash.failure.wait.ms", originalFailureWaitProp);
    } else {
      System.clearProperty("splash.failure.wait.ms");
    }
  }

  @Test
  void mainExitsCleanlyWhenPortIsAlreadyActive() throws IOException {
    try (final ServerSocket socket = new ServerSocket(0)) {
      final int port = socket.getLocalPort();

      // Should return cleanly without starting Spring
      assertDoesNotThrow(
          () ->
              RhytmicRiddlesApplication.main(
                  new String[] {"--server.port=" + port, "--splash.enabled=true"}));
    }
  }

  @Test
  void mainNotifiesSplashAndRethrowsOnStartupFailure() {
    // Port -1 causes SpringApplication to fail fast during web server initialization
    assertThrows(
        Throwable.class,
        () ->
            RhytmicRiddlesApplication.main(
                new String[] {"--server.port=-1", "--splash.enabled=true"}));
  }
}
