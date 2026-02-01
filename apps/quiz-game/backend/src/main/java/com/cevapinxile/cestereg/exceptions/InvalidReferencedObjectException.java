/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.exceptions;

/**
 *
 * @author denijal
 */
public class InvalidReferencedObjectException extends DerivedException {
    
    public InvalidReferencedObjectException(String message){
        super(404, "001", "Invalid referenced object", message);
    }
    
}
