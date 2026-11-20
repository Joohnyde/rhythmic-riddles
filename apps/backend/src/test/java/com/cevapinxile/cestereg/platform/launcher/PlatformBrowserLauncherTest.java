package com.cevapinxile.cestereg.platform.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformBrowserLauncherTest {

  private String originalSplashProp;
  private String originalBrowserProp;

  @BeforeEach
  void setUp() {
    originalSplashProp = System.getProperty("splash.enabled");
    originalBrowserProp = System.getProperty("splash.browser.enabled");
  }

  @AfterEach
  void tearDown() {
    if (originalSplashProp != null) {
      System.setProperty("splash.enabled", originalSplashProp);
    } else {
      System.clearProperty("splash.enabled");
    }

    if (originalBrowserProp != null) {
      System.setProperty("splash.browser.enabled", originalBrowserProp);
    } else {
      System.clearProperty("splash.browser.enabled");
    }
  }

  @Test
  void buildCommandReturnsValidCommandForCurrentPlatform() {
    final String url = "http://localhost:8080/test";
    final List<String> command = PlatformBrowserLauncher.buildCommand(url);

    assertFalse(command.isEmpty());
    assertEquals(url, command.getLast());
  }

  @Test
  void disabledWhenSystemPropertiesSetToFalse() {
    System.setProperty("splash.enabled", "false");
    assertFalse(PlatformBrowserLauncher.isBrowserLaunchEnabled());

    System.clearProperty("splash.enabled");
    System.setProperty("splash.browser.enabled", "false");
    assertFalse(PlatformBrowserLauncher.isBrowserLaunchEnabled());
  }

  @Test
  void enabledWhenExplicitlyForcedTrue() {
    System.setProperty("splash.browser.enabled", "true");
    assertTrue(PlatformBrowserLauncher.isBrowserLaunchEnabled());
  }
}
