/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 *
 * @author denijal
 */
public class MissingArgumentException extends DerivedException{
    
    public MissingArgumentException(String message){
        super(400, "000", "An argument is missing", message);
    }    
    
}
