/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import java.util.UUID;

/**
 *
 * @author denijal
 */

public class CreateTeamResponse {
    
    private UUID id;
    private String name;
    private String image;

    public CreateTeamResponse() {
    }

    public CreateTeamResponse(UUID id, String name, String image) {
        this.id = id;
        this.name = name;
        this.image = image;
    }

    public CreateTeamResponse(TeamEntity choosingTeam) {
        this.id = choosingTeam.getId();
        this.name = choosingTeam.getName();
        this.image = choosingTeam.getImage();
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
    
    public CreateTeamResponse(ChoosingTeam team) {
        if(team != null){
            this.id = UUID.fromString(team.getId());
            this.name = team.getName();
            this.image = team.getImage();
        }
    }
}