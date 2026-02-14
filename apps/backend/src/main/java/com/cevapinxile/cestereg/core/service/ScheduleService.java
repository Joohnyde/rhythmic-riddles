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
 */
public interface ScheduleService {
    
    public void replaySong(UUID lastPlayedScheduleId, String roomCode) throws DerivedException;
    
    public void revealAnswer(UUID lastPlayedScheduleId, String roomCode) throws DerivedException;
    
    public void progress(String roomCode) throws DerivedException;
    
}
