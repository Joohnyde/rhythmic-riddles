/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal
 * Thrown when a required client application is not currently connected to the room.
 *
 * <p>Some actions require both the Admin and TV clients to be present to keep the runtime
 * consistent (playback state, recovery after crashes, progression).</p>
 *
 * <p>This is treated as a service availability issue (HTTP 503), not as a client mistake.</p>
 *
 * <p>Typical throw sites:</p>
 * <ul>
 *   <li>{@code ScheduleServiceImpl.progress(...)} when both apps are required</li>
 *   <li>{@code ScheduleServiceImpl.replaySong(...)} / {@code revealAnswer(...)}</li>
 *   <li>{@code InterruptServiceImpl.resolveErrors(...)} / {@code answer(...)}</li>
 *   <li>{@code CategoryServiceImpl.pickAlbum(...)} when TV must be connected</li>
 * </ul>
 */

public class AppNotRegisteredException extends DerivedException{
    
    public AppNotRegisteredException(String message) {
        super(503, "004", "App not reachable", message);
    }
    
}
