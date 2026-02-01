/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.interfaces;

import com.cevapinxile.cestereg.entities.Igra;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.models.requests.PickAlbumRequest;
import com.cevapinxile.cestereg.models.responses.LastKategorija;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface KategorijaInterface {

    public LastKategorija pick(PickAlbumRequest par, String room_code) throws DerivedException;

    public void start(UUID kategorija_id, String room_code) throws DerivedException;

    public int finishAndNext(Igra igra)  throws DerivedException ;
    
}
