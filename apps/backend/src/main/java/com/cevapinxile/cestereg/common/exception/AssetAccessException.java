/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * Exception thrown when an asset (e.g., audio or image) cannot be accessed.
 *
 * <p>The {@link Reason} indicates whether the asset is missing or exists but cannot be read due to
 * a storage or I/O problem.
 *
 * @author denijal
 */
public class AssetAccessException extends DerivedException {

  /** Describes the reason why the asset access failed. */
  public enum Reason {

    /** The requested asset does not exist. */
    NOT_FOUND,

    /** The asset exists but cannot be read (e.g., storage unavailable). */
    UNREADABLE
  }

  /** Reason for the failure. */
  private final Reason reason;

  /**
   * Creates a new asset access exception.
   *
   * @param reason the reason why the asset access failed
   * @param message detailed error message
   */
  public AssetAccessException(Reason reason, String message) {
    super(
        reason == Reason.NOT_FOUND ? 404 : 503,
        reason == Reason.NOT_FOUND ? "007" : "008",
        reason == Reason.NOT_FOUND ? "Asset Not Found" : "Asset Unavailable",
        message);
    this.reason = reason;
  }
}
