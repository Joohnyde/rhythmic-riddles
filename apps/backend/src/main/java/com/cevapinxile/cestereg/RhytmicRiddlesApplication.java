package com.cevapinxile.cestereg;

import com.cevapinxile.cestereg.platform.launcher.StartupSplash;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RhytmicRiddlesApplication {

  public static void main(String[] args) {
    final StartupSplash splash = StartupSplash.show();
    try {
      SpringApplication.run(RhytmicRiddlesApplication.class, args);
      if (splash != null) {
        splash.close();
      }
    } catch (RuntimeException | Error failure) {
      if (splash != null) {
        splash.failed(failure);
      }
      throw failure;
    }
  }
}
