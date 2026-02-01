/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
class TeamScore{
    
    private UUID teamId;
    private String image;
    private String name;
    private Integer score;
    private UUID scheduleId;
    
    public TeamScore(HashMap<UUID, TeamScore> scoreMap, TeamScoreProjection projection){
        this.teamId = projection.getTeam();
        this.image = projection.getImage();
        this.name = projection.getName();
        this.score = projection.getScore();
        this.scheduleId = projection.getSchedule();
        
        // Self-register
        scoreMap.putIfAbsent(teamId, this);
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(UUID scheduleId) {
        this.scheduleId = scheduleId;
    }

}
public class TeamScoreCache {
    
    private HashMap<UUID, TeamScore> scoreMap = new HashMap<>();
    private List<TeamScore> scoreList = new ArrayList<>();
    
    public TeamScoreCache(List<TeamScoreProjection> projections){
        if(projections != null){
            scoreList = projections.stream().map((projection) -> new TeamScore(scoreMap, projection)).toList();
        }
    }
    
    public Object getScores(){ return this.scoreList; }
    
    public Integer getScore(UUID teamId) throws InvalidReferencedObjectException{ 
        if(!this.scoreMap.containsKey(teamId)) throw new InvalidReferencedObjectException("Team with id "+teamId+" could not be found in the cache");
        return this.scoreMap.get(teamId).getScore();
    }
    
    public void setScore(UUID teamId, UUID scheduleId, Integer score) throws InvalidReferencedObjectException{
        if(!this.scoreMap.containsKey(teamId)) throw new InvalidReferencedObjectException("Team with id "+teamId+" could not be found in the cache");
        TeamScore teamScore = this.scoreMap.get(teamId);
        teamScore.setScore(score);
        teamScore.setScheduleId(scheduleId);
    }
    
}
