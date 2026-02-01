/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public class CategorySimple{

    private UUID id;
    private String name;
    private String image;
    private String pickedByTeam = null;
    private Integer ordinalNumber;

    public CategorySimple() {
    }

    public CategorySimple(CategoryEntity c) {
        this.id = c.getId();
        this.name = c.getAlbumId().getName();
        this.image = c.getAlbumId().getId().toString()+".png";
        if(c.getPickedByTeamId()!= null){
            this.pickedByTeam = c.getPickedByTeamId().getImage();
        }
        this.ordinalNumber = c.getOrdinalNumber();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getPickedByTeam() {
        return pickedByTeam;
    }

    public void setPickedByTeam(String pickedByTeam) {
        this.pickedByTeam = pickedByTeam;
    }

    public Integer getOrdinalNumber() {
        return ordinalNumber;
    }

    public void setOrdinalNumber(Integer ordinalNumber) {
        this.ordinalNumber = ordinalNumber;
    }
    
      
    
}
