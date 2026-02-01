/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.exceptions;

/**
 *
 * @author denijal
 */
public class WrongGameStateException extends DerivedException{
    
    public WrongGameStateException(String message) {
        super(409, "003", "Wrong game-state", message);
    }
    
}
