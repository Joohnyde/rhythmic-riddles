/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Base exception for domain errors that should be returned to clients as a structured API error
 * response.
 *
 * <p>The exception contains an HTTP status code and additional metadata used by the frontend to
 * identify and display the error.
 *
 * <p>Concrete error types in the application should extend this class and define their own status
 * code, error code, and title.
 *
 * @author denijal
 */
@Schema(name = "ErrorResponse", description = "Standard error payload returned by the API.")
public class DerivedException extends Exception {

  /** HTTP status code associated with the error. */
  public final int httpCode;

  /** Stable error identifier used by the frontend. */
  @Schema(example = "E007")
  private final String errorCode;

  /** Short human-readable title describing the error category. */
  @Schema(example = "Asset Not Found")
  private final String title;

  /** Detailed error message describing the failure. */
  @Schema(example = "Answer not found for song 8b49ee99-1799-4e7a-9295-aed8a89c13cb")
  private String message = "";

  /**
   * Creates a new domain exception with the given error metadata.
   *
   * @param httpCode HTTP status code representing the error
   * @param errorCode stable error identifier
   * @param title short human-readable title of the error
   * @param message detailed description of the error
   */
  public DerivedException(int httpCode, String errorCode, String title, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpCode = httpCode;
    this.title = title;
    this.message = message;
  }

  /**
   * Returns a JSON-like string representation of the error.
   *
   * @return formatted error description
   */
  @Override
  public String toString() {
    return "{\"error\":\"E" + errorCode + " - " + title + "\", \"message\":\"" + message + "\"}";
  }
}
