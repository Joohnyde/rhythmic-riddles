/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.exceptions;

/**
 *
 * @author denijal
 */
public class DerivedException extends Exception{
    
    public final int HTTP_CODE;
    private final String ERROR_CODE;
    private final String TITLE;
    
    private String message = "";
    
    public DerivedException(int http_code, String error_code, String title, String message){
        super(message);
        this.ERROR_CODE = error_code;
        this.HTTP_CODE = http_code;
        this.TITLE = title;
        this.message = message;
    }
    
    @Override
    public String toString() {
        return "{\"error\":\"E" + ERROR_CODE + " - " + TITLE + "\" ,\"message\":\"" + message + "\"}";
    }
    
}
