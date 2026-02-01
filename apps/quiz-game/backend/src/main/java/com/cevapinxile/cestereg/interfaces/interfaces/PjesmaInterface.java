/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.interfaces;

import java.io.IOException;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface PjesmaInterface {

    public byte[] play(UUID pjesma_id) throws IOException;
    
    public byte[] reveal(UUID pjesma_id) throws IOException;
    
}
