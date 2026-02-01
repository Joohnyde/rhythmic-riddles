/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.interfaces;

import com.cevapinxile.cestereg.exceptions.DerivedException;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface RedoslijedInterface {
    
    public void refresh(UUID zadnja, String room_code) throws DerivedException;
    
    public void reveal(UUID zadnja, String room_code) throws DerivedException;
    
    public void progress(String room_code) throws DerivedException;
    
}
