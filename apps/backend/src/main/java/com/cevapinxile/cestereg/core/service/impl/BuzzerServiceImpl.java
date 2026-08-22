/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.service.BuzzerService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author denijal
 */
@Service
public class BuzzerServiceImpl implements BuzzerService {

  @Autowired private GameRepository gameRepository;

  @Autowired private TeamRepository teamRepository;

  @Autowired private InterruptService interruptService;

  @Autowired private BroadcastGateway broadcastGateway;

  /* Since this is ran locally in V1, We can be sure that there will be at most one game with state != 3.
      In V2 we'll have to track which buttons were delivered to which customers to be able to link an unassigned button
      to that customer's ongoing game. In any case, a customer should be allowed at most 1 active game at the time

     All buzzes that would happen with a stage change are ignored.
      We don't care about race conditions between buzzes and stage changes so we don't need to lock the game.
  */
  @Override
  public void buzz(final String message) {
    // Sanity checks
    try {

      // Check for the active game
      final Optional<GameEntity> maybeGame = gameRepository.findActive();
      if (maybeGame.isEmpty()) {
        return;
      }

      final GameEntity game = maybeGame.get();
      final String roomCode = game.getCode();
      final int stage = game.getStage();

      // Game is in stage 1 or 3 -- Ignore
      if (stage == 1 || stage == 3) {
        return;
      }

      // Check for the active team with this button
      final Optional<UUID> maybeTeam =
          teamRepository.findIdByButtonAndGameId(message, game.getId());
      final boolean buttonAssigned = maybeTeam.isPresent();

      // Game is in stage 0 and the button is assigned -- Ignore
      if (stage == 0 && buttonAssigned) {
        return;
      }
      // Game is in stage 2 and the team exists -- Interrupt
      if (stage == 2 && buttonAssigned) {
        interruptService.interrupt(roomCode, maybeTeam.get());
      }
      // Game is in stage 0 and the button is UNassigned -- Notify administrator
      if (stage == 0 && !buttonAssigned) {
        broadcastGateway.toAdmin(
            roomCode, "{\"type\":\"button_clicked\",\"buttonCode\":" + message + "}");
      }
    } catch (final NumberFormatException | DerivedException ignored) {
      // Either null, malformed, overflow, etc. or the interrupt produced an error. Ignore both
      // here.
    }
  }

  /* Right now we can do up to 2 database queries AND send a socket message all in vain.
  If slow consider building an in memory object that tracks:
      int stage - Stage of the active game
      String linkedButton - Last button id that was accepted by the frontend
      boolean frontendListening - Will the frontend accept unassigned button socket messages
      HashSet<String> assignedButtons - Buttons whose clicks are to be ignored in stage 0
  Think about how to keep that cache consistent, when should we clear, (re)build it.
  Also figure out how to get the active game for the button optimally (maybe even without a db query)
   */
}
