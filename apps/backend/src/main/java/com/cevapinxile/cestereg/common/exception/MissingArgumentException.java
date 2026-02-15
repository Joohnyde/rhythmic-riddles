/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal
 * Thrown when a required request field or body is missing.
 *
 * <p>Used as a "fail fast" validation error before any business logic runs.</p>
 *
 * <p>Typical throw sites:</p>
 * <ul>
 *   <li>{@code CategoryServiceImpl.pickAlbum(...)} when request body or category_id is missing</li>
 *   <li>{@code TeamServiceImpl.createTeam(...)} when name/picture fields are missing</li>
 * </ul>
 */

public class MissingArgumentException extends DerivedException{
    
    public MissingArgumentException(String message){
        super(400, "000", "An argument is missing", message);
    }    
    
}
