/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.models.responses;

import com.cevapinxile.cestereg.entities.Kategorija;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public class KategorijaSimple{

    public UUID id;
    public String ime;
    public String slika;
    public String picked = null;
    public Integer rbr;

    public KategorijaSimple() {
    }

    public KategorijaSimple(Kategorija t) {
        this.id = t.getId();
        this.ime = t.getAlbumId().getNaziv();
        this.slika = t.getAlbumId().getId().toString()+".png";
        if(t.getTimBirao() != null){
            this.picked = t.getTimBirao().getSlika();
        }
        this.rbr = t.getRbr();
    }
    
    
    
}
