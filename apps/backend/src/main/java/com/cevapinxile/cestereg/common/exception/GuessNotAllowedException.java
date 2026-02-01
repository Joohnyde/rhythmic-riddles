/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal
 * Thrown when a team guess/buzz action is not allowed under the current runtime conditions.
 *
 * <p>Used to enforce rules like:</p>
 * <ul>
 *   <li>a team can only guess once per song</li>
 *   <li>guesses are only allowed while the snippet is actively playing</li>
 *   <li>only one team may be in the answering state at a time</li>
 *   <li>no guessing while the game is paused by a system interrupt</li>
 * </ul>
 *
 * <p>Typical throw sites:</p>
 * <ul>
 *   <li>{@code InterruptServiceImpl.interrupt(...)} when buzz/guess is disallowed</li>
 *   <li>{@code InterruptServiceImpl.answer(...)} when the guess has already been resolved</li>
 * </ul>
 */

public class GuessNotAllowedException extends DerivedException {
    
    public GuessNotAllowedException(String message) {
        super(409, "006", "Guess wasn't allowed", message);
    }
    
}
