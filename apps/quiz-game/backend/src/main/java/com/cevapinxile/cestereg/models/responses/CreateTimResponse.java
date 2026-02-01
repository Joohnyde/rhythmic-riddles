/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.models.responses;

import java.util.UUID;

/**
 *
 * @author denijal
 */

public class CreateTimResponse {
    
    private UUID id;
    private String ime;
    private String slika;

    public CreateTimResponse() {
    }

    public CreateTimResponse(UUID id, String ime, String slika) {
        this.id = id;
        this.ime = ime;
        this.slika = slika;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIme() {
        return ime;
    }

    public void setIme(String ime) {
        this.ime = ime;
    }

    public String getSlika() {
        return slika;
    }

    public void setSlika(String slika) {
        this.slika = slika;
    }
           
    
    
    public CreateTimResponse(ChoosingTeam birac) {
        if(birac != null){
            this.id = UUID.fromString(birac.getId());
            this.ime = birac.getNaziv();
            this.slika = birac.getSlika();
        }
    }
}