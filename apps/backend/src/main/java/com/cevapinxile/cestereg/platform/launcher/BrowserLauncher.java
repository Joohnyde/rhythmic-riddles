/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.platform.launcher;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

/**
 *
 * @author denijal
 */
@Component
@Profile("production")
public class BrowserLauncher {

    private final ServerProperties serverProperties;

    public BrowserLauncher(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        try {
            String address = serverProperties.getAddress() != null
                    ? serverProperties.getAddress().getHostAddress()
                    : "localhost";

            if ("0.0.0.0".equals(address)) {
                address = "localhost";
            }

            int port = serverProperties.getPort() != null
                    ? serverProperties.getPort()
                    : 8080;

            String url = "http://" + address + ":" + port;

            // Try Desktop first
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }

            // Linux fallback
            new ProcessBuilder("xdg-open", url).start();

        } catch (Exception ignored) {}
    }
}
