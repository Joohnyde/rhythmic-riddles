/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface SongService {

    public byte[] playSnippet(UUID songId) throws DerivedException;
    
    public byte[] playAnswer(UUID songId) throws DerivedException;
    
}
