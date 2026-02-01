/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.repositories;

import com.cevapinxile.cestereg.entities.Tim;
import com.cevapinxile.cestereg.models.responses.ChoosingTeam;
import com.cevapinxile.cestereg.models.responses.CreateTimResponse;
import com.cevapinxile.cestereg.models.responses.TeamScoreProjection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 *
 * @author denijal
 */
public interface TimRepository extends JpaRepository<Tim, UUID> {
    
    public List<CreateTimResponse> findByIgra(String kod);
    
    @Query(value = """
                   SELECT t.id, t.naziv, t.slika
                   FROM tim t
                       LEFT JOIN Kategorija k ON (t.id = k.tim_birao)
                   WHERE t.igra_id = :igra
                   GROUP BY t.id
                   HAVING COUNT(k.tim_birao) < FLOOR(:totalalbums / (SELECT COUNT(*) FROM tim t1 WHERE t1.igra_id = :igra))
                   ORDER BY COUNT(k.tim_birao) ASC, t.id ASC
                   LIMIT 1
                   """, nativeQuery = true)
    
    public ChoosingTeam findNext(@Param("igra") UUID igra, @Param("totalalbums") int totalalbums);
    
    @Query(
        value = """
        SELECT DISTINCT ON (t.id)
               t.id AS tim,
               t.slika,
               t.naziv,
               COALESCE(o.bodovi, 0) AS bodovi,
               o.redoslijed_id AS odgovarao
        FROM Tim t
        LEFT JOIN Odgovor o
               ON o.tim_id = t.id
              AND o.bodovi IS NOT NULL
        LEFT JOIN Igra i
               ON t.igra_id = i.id
        WHERE i.kod = ?1
        ORDER BY t.id, o.vreme_stigao DESC
        """,
        nativeQuery = true)
      public List<TeamScoreProjection> getTeamScores(String kod);
    
}
