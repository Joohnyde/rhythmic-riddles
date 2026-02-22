/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.platform.db.embedded;

import java.nio.file.Path;

public final class AppDataDirs {
    private AppDataDirs() {}

    public static Path appDataBaseDir() {
        String os = System.getProperty("os.name").toLowerCase();

        // Windows: %LOCALAPPDATA%\cestereg
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "cestereg");
            }
            // fallback
            String home = System.getProperty("user.home");
            return Path.of(home, "AppData", "Local", "cestereg");
        }

        // macOS: ~/Library/Application Support/cestereg
        if (os.contains("mac")) {
            String home = System.getProperty("user.home");
            return Path.of(home, "Library", "Application Support", "cestereg");
        }

        // Linux: $XDG_DATA_HOME/cestereg or ~/.local/share/cestereg
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg, "cestereg");
        }
        String home = System.getProperty("user.home");
        return Path.of(home, ".local", "share", "cestereg");
    }
}