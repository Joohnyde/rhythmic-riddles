package com.cevapinxile.cestereg;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cevapinxile.cestereg.platform.launcher.StartupSplash;
import org.junit.jupiter.api.Test;

class RhytmicRiddlesApplicationStartupTest {

  @Test
  void nativeSplashAssetsArePackaged() {
    assertNotNull(StartupSplash.class.getResource("/splash/starting.png"));
    assertNotNull(StartupSplash.class.getResource("/splash/orbit.png"));
  }

  @Test
  void splashDoesNotStartInHeadlessMode() {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      assertNull(StartupSplash.show());
    }
  }
}
