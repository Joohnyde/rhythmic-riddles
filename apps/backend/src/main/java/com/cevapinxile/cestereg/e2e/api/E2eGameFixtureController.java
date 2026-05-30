/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.e2e.api;

import com.cevapinxile.cestereg.common.util.RoomCodePath;
import com.cevapinxile.cestereg.e2e.dto.E2eGameFixtureRequest;
import com.cevapinxile.cestereg.e2e.service.E2eGameFixtureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author denijal
 */
@RestController
@Profile("e2e")
@RequestMapping("/api/e2e/v1/game-fixtures")
class E2eGameFixtureController {
    
    @Autowired
    private E2eGameFixtureService e2eGameFixtureService;

    @DeleteMapping("{roomCode}")
    public ResponseEntity<Void> deleteRoom(@RoomCodePath @PathVariable String roomCode) {
        e2eGameFixtureService.resetRuntimeState(roomCode);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<Void> createFixture(@RequestBody E2eGameFixtureRequest request) {
        e2eGameFixtureService.createFixture(request);
        return ResponseEntity.ok().build();
    }
}