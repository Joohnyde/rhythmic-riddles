/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.platform.db.embedded;

import java.nio.file.Path;

public final class AppDataDirs {
    private AppDataDirs() {}

    /** Linux-first implementation; you can extend later for Windows/macOS. */
    public static Path appDataBaseDir() {
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg, "cestereg");
        }
        String home = System.getProperty("user.home");
        return Path.of(home, ".local", "share", "cestereg");
    }
}