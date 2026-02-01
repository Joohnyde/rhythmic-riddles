/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.exceptions;

/**
 *
 * @author denijal
 */
public class GuessNotAllowedException extends DerivedException {
    
    public GuessNotAllowedException(String message) {
        super(409, "006", "Guess wasn't allowed", message);
    }
    
}
