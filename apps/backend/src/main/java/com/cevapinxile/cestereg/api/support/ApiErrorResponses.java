/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.support;

/**
 * @author denijal
 */
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InternalServerErrorException;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ApiErrorResponses {

  private ApiErrorResponses() {}

  public static ResponseEntity<String> handleApiException(Logger log, Exception ex) {
    if (ex instanceof DerivedException derivedException) {
      return fromDerived(log, derivedException);
    }
    return fromUnexpected(log, ex);
  }

  private static ResponseEntity<String> fromDerived(Logger log, DerivedException ex) {
    log.info(ex.toString());
    return ResponseEntity.status(ex.httpCode)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ex.toString());
  }

  private static ResponseEntity<String> fromUnexpected(Logger log, Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(500)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new InternalServerErrorException().toString());
  }
}
