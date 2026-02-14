/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.repository;

import com.cevapinxile.cestereg.api.quiz.dto.response.ChoosingTeam;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.TeamScoreProjection;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 *
 * @author denijal
 */
public interface TeamRepository extends JpaRepository<TeamEntity, UUID> {
    
    public List<CreateTeamResponse> findByGameId(String roomCode);
    
    @Query(value = """
                   SELECT t.id,
                          t.name,
                          t.image
                   FROM   team t
                          LEFT JOIN category c
                                 ON ( t.id = c.picked_by_team_id )
                   WHERE  t.game_id = :gameId
                   GROUP  BY t.id
                   HAVING COUNT(c.picked_by_team_id) < FLOOR(:totalAlbums / (SELECT COUNT(*)
                                                                             FROM   team t1
                                                                             WHERE
                                                             t1.game_id = :gameId))
                   ORDER  BY COUNT(c.picked_by_team_id) ASC,
                             t.id ASC
                   LIMIT  1 
                   """, nativeQuery = true)
    
    public ChoosingTeam findNext(@Param("gameId") UUID gameId, @Param("totalAlbums") int totalAlbums);
    
    @Query(
        value = """
        SELECT DISTINCT ON (t.id) 
                        t.id                                AS team,
                        t.image,
                        t.NAME,
                        COALESCE(i.score_or_scenario_id, 0) AS score,
                        i.schedule_id                       AS schedule
        FROM            team t
        LEFT JOIN       interrupt i
        ON              i.team_id = t.id
        AND             i.score_or_scenario_id IS NOT NULL
        LEFT JOIN       Game g
        ON              t.game_id = g.id
        WHERE           g.code= ?1
        ORDER BY        t.id,
                        i.arrived_at DESC
        """,
        nativeQuery = true)
      public List<TeamScoreProjection> getTeamScores(String roomCode);
    
}
