/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.interfaces;

import com.cevapinxile.cestereg.entities.Igra;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.models.requests.CreateIgraRequest;
import java.util.HashMap;
import java.util.Optional;

/**
 *
 * @author denijal
 */
public interface IgraInterface {
    
    public String createIgra(CreateIgraRequest cir) throws DerivedException;
    
    public HashMap<String, Object> contextFetch(String code) throws DerivedException;

    public void changeState(int stage_id, String room_code) throws DerivedException;

    public int getState(String room_code);
    
    public Igra isChangeStateLegal(int stage_id, String room_code) throws DerivedException;
    
    public Optional<Igra> findByKod(String room_code);
    
}
