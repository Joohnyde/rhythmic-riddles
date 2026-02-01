/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal
 * Thrown when a request is not authorized to act on the target game/room.
 *
 * <p>In this project it is primarily used when a team attempts to perform an action
 * in a room it does not belong to.</p>
 *
 * <p>Typical throw sites:</p>
 * <ul>
 *   <li>{@code InterruptServiceImpl.interrupt(...)} when the team is not registered in the room/game</li>
 * </ul>
 */

public class UnauthorizedException extends DerivedException {
    
    public UnauthorizedException(String message) {
        super(401, "005", "Unauthorized request", message);
    }
    
}
