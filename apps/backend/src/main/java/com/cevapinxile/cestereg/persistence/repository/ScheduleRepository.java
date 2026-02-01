/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.repository;

import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author denijal
 */
public interface ScheduleRepository extends JpaRepository<ScheduleEntity, UUID>{
    
    public ScheduleEntity findLastPlayed(UUID gameId);
    
    public Optional<ScheduleEntity> findNext(UUID gameId);
    
}
