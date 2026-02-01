/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.repositories;

import com.cevapinxile.cestereg.entities.Redoslijed;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author denijal
 */
public interface RedoslijedRepository extends JpaRepository<Redoslijed, UUID>{
    
    public Redoslijed findZadnja(UUID igraId);
    
    public Optional<Redoslijed> getNext(UUID igraId);
    
}
