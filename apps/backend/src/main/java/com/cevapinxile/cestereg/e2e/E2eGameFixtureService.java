/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.e2e;

import com.cevapinxile.cestereg.common.exception.E2eGameFixtureValidationException;

/**
 * @author denijal
 */
public interface E2eGameFixtureService {

  void resetRuntimeState(String roomCode);

  void createFixture(E2eGameFixtureRequest request) throws E2eGameFixtureValidationException;

  void attachCatalog(String roomCode, E2eCatalogFixtureRequest request)
      throws E2eGameFixtureValidationException;
}
