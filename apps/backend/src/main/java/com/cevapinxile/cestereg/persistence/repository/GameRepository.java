/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.repository;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/*
 * @author denijal
 */
public interface GameRepository extends JpaRepository<GameEntity, UUID> {

  Optional<GameEntity> findByCode(String roomCode);

  /**
   * Serializes a must-persist event for one room. Unlike {@link #tryLockGame(String)}, this waits
   * for a short in-flight transaction instead of dropping the event because the room is busy.
   * @param roomCode unique room identifier
   * @return UUID of the game whose code corresponds to roomCode parameter.
   */
  @Query(
      value = "SELECT id FROM game WHERE code = :roomCode FOR UPDATE",
      nativeQuery = true)
  Optional<UUID> lockGame(@Param("roomCode") String roomCode);

  /**
   * Acquires the per-game row lock used to serialize state-changing commands for one room.
   * NOWAIT intentionally fails a competing command instead of letting it run after the winner.
   * @param roomCode unique room identifier
   * @return UUID of the game whose code corresponds to roomCode parameter.
   */
  @Query(
      value = "SELECT id FROM game WHERE code = :roomCode FOR UPDATE NOWAIT",
      nativeQuery = true)
  Optional<UUID> tryLockGame(@Param("roomCode") String roomCode);

  /**
   * Loads a game by its room code and validates that it is currently in the expected stage.
   *
   * <p>Standard workflow:
   *
   * <ol>
   *   <li>Fetch the game by {@code roomCode}
   *   <li>Throw a {@link DerivedException} if the game does not exist
   *   <li>Verify that the game is in the specified {@code stage}
   *   <li>Throw a {@link DerivedException} if the stage does not match
   * </ol>
   *
   * <p>This method centralizes existence and state validation to prevent duplicated checks across
   * services.
   *
   * @param roomCode unique room identifier
   * @param stageId expected current stage of the game
   * @return the loaded {@code GameEntity} if it exists and matches the expected stage
   * @throws InvalidReferencedObjectException if the game does not exist
   * @throws WrongGameStateException if the game is not in the expected stage
   */
  default GameEntity findByCode(final String roomCode, final Integer stageId)
      throws InvalidReferencedObjectException, WrongGameStateException {
    final Optional<GameEntity> maybeGame = findByCode(roomCode);
    if (maybeGame.isEmpty()) {
      throw new InvalidReferencedObjectException("Game with code " + roomCode + " does not exist");
    }
    final GameEntity game = maybeGame.get();
    if (stageId != null && game.getStage() != stageId) {
      switch (stageId) {
        case 0 ->
            throw new WrongGameStateException("Game with code " + roomCode + " already started");
        case 1 ->
            throw new WrongGameStateException(
                "Game with code " + roomCode + " doesn't choose albums now");
        case 2 ->
            throw new WrongGameStateException(
                "Game with code " + roomCode + " is not in the song listening stage");
        default ->
            throw new WrongGameStateException(
                "Game with code " + roomCode + " is in a non-existing stage " + stageId);
      }
    }
    return game;
  }

  @Modifying
  @Query(value = "DELETE FROM game WHERE code = :roomCode", nativeQuery = true)
  void deleteByCode(@Param("roomCode") String roomCode);
}
