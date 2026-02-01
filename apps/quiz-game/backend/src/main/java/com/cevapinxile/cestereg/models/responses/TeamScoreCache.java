/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.models.responses;

import com.cevapinxile.cestereg.exceptions.InvalidReferencedObjectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
class TeamScore{
    
    public UUID tim;
    public String slika;
    public String naziv;
    public Integer bodovi;
    public UUID odgovarao;
    
    public TeamScore(HashMap<UUID, TeamScore> mapa, TeamScoreProjection projection){
        this.tim = projection.getTim();
        this.slika = projection.getSlika();
        this.naziv = projection.getNaziv();
        this.bodovi = projection.getBodovi();
        this.odgovarao = projection.getOdgovarao();
        
        //Self-register
        mapa.putIfAbsent(tim, this);
    }

}
public class TeamScoreCache {
    
    private HashMap<UUID, TeamScore> mapa = new HashMap<>();
    private List<TeamScore> scores = new ArrayList<>();
    
    public TeamScoreCache(List<TeamScoreProjection> projections){
        if(projections != null){
            scores = projections.stream().map((projection) -> new TeamScore(mapa, projection)).toList();
        }
    }
    
    public List<TeamScore> getScores(){ return this.scores; }
    
    public Integer getBodovi(UUID tim_id) throws InvalidReferencedObjectException{ 
        if(!this.mapa.containsKey(tim_id)) throw new InvalidReferencedObjectException("Team with id "+tim_id+" could not be found in the cache");
        return this.mapa.get(tim_id).bodovi;
    }
    
    public void setOdgovarao(UUID tim_id, UUID redoslijed_id, Integer bodovi) throws InvalidReferencedObjectException{
        if(!this.mapa.containsKey(tim_id)) throw new InvalidReferencedObjectException("Team with id "+tim_id+" could not be found in the cache");
        TeamScore teamScore = this.mapa.get(tim_id);
        teamScore.bodovi = bodovi;
        teamScore.odgovarao = redoslijed_id;
    }
    
}
