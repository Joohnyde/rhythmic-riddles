/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal
 * Thrown when request arguments are syntactically present but semantically invalid.
 *
 * <p>Examples include out-of-range values, logically inconsistent identifiers,
 * or constraints that cannot be satisfied (e.g., not enough songs in a category).</p>
 *
 * <p>Typical throw sites:</p>
 * <ul>
 *   <li>{@code GameServiceImpl.createGame(...)} for invalid album/song counts</li>
 *   <li>{@code GameServiceImpl.isChangeStageLegal(...)} for invalid stage transitions</li>
 *   <li>{@code CategoryServiceImpl.startCategory(...)} when a category cannot satisfy required song count</li>
 *   <li>{@code InterruptServiceImpl.savePreviousScenario(...)} for invalid scenario ids</li>
 * </ul>
 */

public class InvalidArgumentException extends DerivedException {

    public InvalidArgumentException(String message) {
        super(422, "002", "Malformed argument", message);
    }
}
