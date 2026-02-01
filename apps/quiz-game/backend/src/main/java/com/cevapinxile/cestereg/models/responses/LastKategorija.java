/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.models.responses;

import com.cevapinxile.cestereg.entities.Kategorija;
import com.cevapinxile.cestereg.entities.Tim;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public class LastKategorija {
    public UUID kategorija;
    public KategorijaPreview izabrana;
    public String ime;
    public String slika;
    
    public CreateTimResponse birac = null;
    public boolean started = false;
    public int rbr;
    
    public LastKategorija(){}
    
    public LastKategorija(Kategorija k){
        this.kategorija = k.getId();
        this.izabrana = new KategorijaPreview(k.getAlbumId().getNaziv(), k.getAlbumId().getId()+".png");
        Tim birac = k.getTimBirao();
        if(birac != null) this.birac = new CreateTimResponse(birac.getId(), birac.getNaziv(), birac.getSlika());
        if(k.getGotovo() != null) this.started = k.getGotovo();
        this.rbr = k.getRbr();
    }
    
    
}
