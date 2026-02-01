/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.exceptions;

/**
 *
 * @author denijal
 */
public class AppNotRegisteredException extends DerivedException{
    
    public AppNotRegisteredException(String message) {
        super(503, "004", "App not reachable", message);
    }
    
}
