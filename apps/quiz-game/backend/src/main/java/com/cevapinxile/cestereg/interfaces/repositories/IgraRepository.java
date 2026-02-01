/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.repositories;

import com.cevapinxile.cestereg.entities.Igra;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author denijal
 */
public interface IgraRepository extends JpaRepository<Igra, Object> {
    
    public Optional<Igra> findByKod(String kod);
    
}
