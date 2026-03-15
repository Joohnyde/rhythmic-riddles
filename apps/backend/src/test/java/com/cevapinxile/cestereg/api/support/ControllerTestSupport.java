package com.cevapinxile.cestereg.api.support;

import com.cevapinxile.cestereg.common.exception.DerivedException;

public abstract class ControllerTestSupport {

  protected static FakeDerivedException derived(
      int status, String code, String title, String message) {
    return new FakeDerivedException(status, code, title, message);
  }

  protected static final class FakeDerivedException extends DerivedException {
    private FakeDerivedException(int status, String code, String title, String message) {
      super(status, code, title, message);
    }
  }
}
