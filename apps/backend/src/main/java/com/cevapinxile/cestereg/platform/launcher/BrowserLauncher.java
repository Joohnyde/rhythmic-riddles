/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.platform.launcher;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class BrowserLauncher {

  private final ServerProperties serverProperties;

  public BrowserLauncher(ServerProperties serverProperties) {
    this.serverProperties = serverProperties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void openBrowser() {
    final String url = buildUrl();

    // 1) Try Java Desktop first (works on many desktop sessions)
    if (tryDesktopBrowse(url)) {
      return;
    }

    // 2) OS-specific fallbacks (works when Desktop is not supported / headless / service)
    try {
      openWithOsCommand(url);
    } catch (IOException ignored) {
      // If this fails too, we just don't auto-open. App still runs.
    }
  }

  private String buildUrl() {
    String address =
        serverProperties.getAddress() != null
            ? serverProperties.getAddress().getHostAddress()
            : "localhost";

    // When binding to 0.0.0.0, browser must still use localhost
    if ("0.0.0.0".equals(address)) {
      address = "localhost";
    }

    final int port = (serverProperties.getPort() != null) ? serverProperties.getPort() : 8080;

    return "http://" + address + ":" + port;
  }

  private boolean tryDesktopBrowse(final String url) {
    try {
      if (!Desktop.isDesktopSupported()) {
        return false;
      }
      final Desktop d = Desktop.getDesktop();
      if (!d.isSupported(Desktop.Action.BROWSE)) {
        return false;
      }
      d.browse(new URI(url));
      return true;
    } catch (IOException | URISyntaxException e) {
      return false; // fall back to OS command
    }
  }

  private void openWithOsCommand(final String url) throws IOException {
    final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    if (os.contains("win")) {
      // "start" is a cmd built-in; empty title "" prevents URL being treated as title
      new ProcessBuilder("cmd", "/c", "start", "", url).start();
      return;
    }

    if (os.contains("mac")) {
      new ProcessBuilder("open", url).start();
      return;
    }

    // Linux / other Unix
    new ProcessBuilder("xdg-open", url).start();
  }
}
