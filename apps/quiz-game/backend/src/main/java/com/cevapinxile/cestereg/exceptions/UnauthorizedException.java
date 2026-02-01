/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.exceptions;

/**
 *
 * @author denijal
 */
public class UnauthorizedException extends DerivedException {
    
    public UnauthorizedException(String message) {
        super(401, "005", "Unauthorized request", message);
    }
    
}
