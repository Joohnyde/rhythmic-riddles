package com.cevapinxile.cestereg;

import com.cevapinxile.cestereg.platform.launcher.StartupSplashServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

@SpringBootApplication
public class RhytmicRiddlesApplication {

  public static void main(final String[] args) {
    final StartupSplashServer splashServer = StartupSplashServer.start(args);
    if (splashServer != null && splashServer.isAlreadyRunning()) {
      return;
    }

    try {
      final SpringApplication app = new SpringApplication(RhytmicRiddlesApplication.class);
      if (splashServer != null) {
        app.addListeners(
            (ApplicationListener<ApplicationReadyEvent>) event -> splashServer.markReady());
      }
      app.run(args);
      if (splashServer != null) {
        splashServer.markReady();
      }
    } catch (final Throwable throwable) {
      if (splashServer != null) {
        splashServer.handleStartupFailure(throwable);
      }
      throw throwable;
    }
  }
}
