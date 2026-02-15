/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;

/**
 *
 * @author denijal
 * Service responsible for scheduling and progressing from a finished song.
 *
 * <p>Progression methods ensure the game moves through stages deterministically and defensively,
 * resolving any pending pause/error state before advancing to prevent inconsistent timelines.</p>
 */
public interface ScheduleService {
    
    public void replaySong(UUID lastPlayedScheduleId, String roomCode) throws DerivedException;
    
    public void revealAnswer(UUID lastPlayedScheduleId, String roomCode) throws DerivedException;
    
    /**
    * Advances the game to the next logical step in the runtime flow.
    *
    * <p>This may include: starting the next song, moving to the next category,
    * or finishing the game when content is exhausted. Implementations should resolve pending interrupts
    * (system pause/error) before advancing.</p>
    *
    * @param roomCode room identifier
    * @throws DerivedException if progression is not valid for the current game state
    */
    public void progress(String roomCode) throws DerivedException;
    
}
