package com.cevapinxile.cestereg.common.exception;

/** Raised when another request is already changing the same game room. */
public class RoomBusyException extends DerivedException {

  public RoomBusyException(final String message) {
    super(423, "010", "Room busy", message);
  }
}
