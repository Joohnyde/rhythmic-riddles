/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal Thrown when an operation is attempted while the game is in an incompatible
 *     stage/state.
 *     <p>This is used to enforce the stage machine (lobby → stage 1 → stage 2 → finish) and prevent
 *     illegal transitions or actions at the wrong time.
 *     <p>Typical throw sites:
 *     <ul>
 *       <li>{@code GameServiceImpl.isChangeStageLegal(...)} for invalid stage transitions
 *       <li>{@code CategoryServiceImpl.pickAlbum(...)} when album selection is not active
 *       <li>{@code GameRepository.findByCode(roomCode, stage)} when a caller expects a specific
 *           stage
 *     </ul>
 */
public class WrongGameStateException extends DerivedException {

  public WrongGameStateException(String message) {
    super(409, "003", "Wrong game-state", message);
  }
}
