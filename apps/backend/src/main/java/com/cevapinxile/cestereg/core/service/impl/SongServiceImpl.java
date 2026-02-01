/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.core.gateway.AssetGateway;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.cevapinxile.cestereg.core.service.SongService;

/**
 *
 * @author denijal
 */
@Service
public class SongServiceImpl implements SongService{

    @Autowired
    private AssetGateway assetGateway;


    @Override
     public byte[] playSnippet(UUID songId) throws DerivedException {
        return assetGateway.readSnippetMp3(songId);
    }  

    @Override
    public byte[] playAnswer(UUID songId) throws DerivedException {
        return assetGateway.readAnswerMp3(songId);
    }
}
