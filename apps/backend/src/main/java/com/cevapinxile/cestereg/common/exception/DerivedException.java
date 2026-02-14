/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * @author denijal
 */
@Schema(name = "ErrorResponse", description = "Standard error payload returned by the API.")
public class DerivedException extends Exception{
    
    public final int HTTP_CODE;
    
    @Schema(example = "E007")
    private final String ERROR_CODE;
    
    @Schema(example = "Asset Not Found")
    private final String TITLE;
    
    @Schema(example = "Answer not found for song 8b49ee99-1799-4e7a-9295-aed8a89c13cb")
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
