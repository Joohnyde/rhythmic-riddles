/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.repositories;

import com.cevapinxile.cestereg.entities.Odgovor;
import com.cevapinxile.cestereg.models.responses.InterruptFrame;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/**
 *
 * @author denijal
 */
public interface OdgovorRepository extends JpaRepository<Odgovor, UUID> {
    
    public List<InterruptFrame> findInterrupts(LocalDateTime start_timestamp, UUID red_id);
    
    public Odgovor findLastPause(LocalDateTime start_timestamp, UUID red_id);
    
    public Odgovor findLastAnswer(LocalDateTime start_timestamp, UUID red_id);

    public Optional<Boolean> isTimOdgovarao(UUID timId);
    
    @Modifying
    public void resolveError(UUID redoslijedId, LocalDateTime end);

    public UUID findCorrectAnswer(UUID id);
    
    public Integer getPreviousScenario(UUID redoslijedId);
    
}
