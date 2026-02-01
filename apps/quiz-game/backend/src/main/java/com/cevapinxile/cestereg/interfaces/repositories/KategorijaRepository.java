/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.repositories;

import com.cevapinxile.cestereg.entities.Kategorija;
import com.cevapinxile.cestereg.models.responses.KategorijaSimple;
import com.cevapinxile.cestereg.models.responses.LastKategorija;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author denijal
 */
public interface KategorijaRepository extends JpaRepository<Kategorija, UUID> {
    
    public List<KategorijaSimple> findByIgra(UUID igra);
//    public List<Kategorija> findByIgra(UUID igra);
    
    public LastKategorija findLast(UUID igra);

    public Integer getNextID(UUID igra);
    
}
