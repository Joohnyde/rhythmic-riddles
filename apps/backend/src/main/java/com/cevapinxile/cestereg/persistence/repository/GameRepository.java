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

/**
 *
 * @author denijal
 */
public interface GameRepository extends JpaRepository<GameEntity, UUID> {
    
    public Optional<GameEntity> findByCode(String roomCode);
    
    /**
    * Loads a game by its room code and validates that it is currently in the expected stage.
    *
    * <p>Standard workflow:</p>
    * <ol>
    *   <li>Fetch the game by {@code roomCode}</li>
    *   <li>Throw a {@link DerivedException} if the game does not exist</li>
    *   <li>Verify that the game is in the specified {@code stage}</li>
    *   <li>Throw a {@link DerivedException} if the stage does not match</li>
    * </ol>
    *
    * <p>This method centralizes existence and state validation to prevent
    * duplicated checks across services.</p>
    *
    * @param roomCode unique room identifier
    * @param stageId expected current stage of the game
    * @return the loaded {@code GameEntity} if it exists and matches the expected stage
    * @throws DerivedException if the game does not exist or is not in the expected stage
    */
    default GameEntity findByCode(String roomCode, Integer stageId) throws DerivedException{
        Optional<GameEntity> maybeGame = findByCode(roomCode);
        if(maybeGame.isEmpty()) 
            throw new InvalidReferencedObjectException("Game with code "+roomCode+" does not exist");
        GameEntity game = maybeGame.get();
        if(stageId != null && game.getStage() != stageId) {
            switch (stageId) {
                case 0 -> throw new WrongGameStateException("Game with code "+roomCode+" already started");
                case 1 -> throw new WrongGameStateException("Game with code "+roomCode+" doesn't choose albums now");
                case 2 -> throw new WrongGameStateException("Game with code "+roomCode+" is not in the song listening stage");
            }
        }
        return game;
    }
    
}
