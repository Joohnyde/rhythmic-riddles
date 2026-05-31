/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.repository;

import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author denijal
 */
public interface TrackRepository extends JpaRepository<TrackEntity, UUID> {}
