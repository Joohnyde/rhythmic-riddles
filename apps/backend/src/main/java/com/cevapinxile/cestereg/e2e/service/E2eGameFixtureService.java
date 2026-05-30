/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.e2e.service;

import com.cevapinxile.cestereg.e2e.dto.E2eGameFixtureRequest;

/**
 *
 * @author denijal
 */
public interface E2eGameFixtureService {

    public void resetRuntimeState(String roomCode);

    public void createFixture(E2eGameFixtureRequest request);
    
}
