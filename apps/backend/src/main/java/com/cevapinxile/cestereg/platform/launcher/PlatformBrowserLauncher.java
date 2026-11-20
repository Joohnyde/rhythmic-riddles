package com.cevapinxile.cestereg.platform.launcher;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Cross-platform browser launcher that uses operating system CLI commands rather than
 * java.awt.Desktop to prevent macOS dock icon artifacts and avoid AWT subsystem initialization
 * issues on headless Linux systems.
 */
public final class PlatformBrowserLauncher {

  private PlatformBrowserLauncher() {}

  /**
   * Determines whether browser auto-launch is enabled in the current environment.
   *
   * @return true if browser launch is enabled, false otherwise.
   */
  public static boolean isBrowserLaunchEnabled() {
    final String splashEnabled = System.getProperty("splash.enabled");
    if ("false".equalsIgnoreCase(splashEnabled)) {
      return false;
    }

    final String browserEnabled = System.getProperty("splash.browser.enabled");
    if ("false".equalsIgnoreCase(browserEnabled)) {
      return false;
    }

    final String envSplash = System.getenv("SPLASH_ENABLED");
    if ("false".equalsIgnoreCase(envSplash)) {
      return false;
    }

    final String envBrowser = System.getenv("SPLASH_BROWSER_ENABLED");
    if ("false".equalsIgnoreCase(envBrowser)) {
      return false;
    }

    // Explicit enablement overrides automated environment detection
    if ("true".equalsIgnoreCase(splashEnabled) || "true".equalsIgnoreCase(browserEnabled)) {
      return true;
    }

    // Disable in CI environments by default
    if (System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null) {
      return false;
    }

    // Disable if JVM headless property is explicitly true
    if (Boolean.getBoolean("java.awt.headless")) {
      return false;
    }

    // Disable on Linux if no graphic display server is available
    final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("mac") && !os.contains("win")) {
      final String display = System.getenv("DISPLAY");
      final String wayland = System.getenv("WAYLAND_DISPLAY");
      if ((display == null || display.isBlank()) && (wayland == null || wayland.isBlank())) {
        return false;
      }
    }

    return true;
  }

  /**
   * Builds the OS-specific command list for launching a URL in the default browser.
   *
   * @param url the URL to open.
   * @return list of command arguments for ProcessBuilder.
   */
  public static List<String> buildCommand(final String url) {
    final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    if (os.contains("mac") || os.contains("darwin")) {
      return List.of("open", url);
    }

    if (os.contains("win")) {
      final String comspec = System.getenv("COMSPEC");
      final String cmd = (comspec != null && !comspec.isBlank()) ? comspec : "cmd.exe";
      return List.of(cmd, "/c", "start", "", url);
    }

    // Linux and other Unix variants
    return List.of("xdg-open", url);
  }

  /**
   * Opens the given URL in the default browser using native OS commands.
   *
   * @param url the URL to launch.
   * @return true if the launch process was successfully started, false otherwise.
   */
  public static boolean openBrowser(final String url) {
    if (!isBrowserLaunchEnabled()) {
      return false;
    }

    final List<String> command = buildCommand(url);
    try {
      new ProcessBuilder(command).start();
      return true;
    } catch (final IOException e) {
      System.err.println(
          "[Splash] Warning: Failed to launch browser with command "
              + command
              + ": "
              + e.getMessage());
      return false;
    }
  }
}
