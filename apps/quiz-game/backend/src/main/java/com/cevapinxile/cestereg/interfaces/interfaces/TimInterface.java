/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.interfaces;

import com.cevapinxile.cestereg.entities.Tim;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.models.requests.CreateTimRequest;
import com.cevapinxile.cestereg.models.responses.ChoosingTeam;
import com.cevapinxile.cestereg.models.responses.CreateTimResponse;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface TimInterface {
    
    public CreateTimResponse createTim(CreateTimRequest ctr, String room_code) throws DerivedException;
    
    public void kick(String tim_id, String room_code) throws DerivedException;
    
    public Object getTeamScores(String kod) throws DerivedException;
    
    public Integer getTeamPoints(UUID tim_Id, String kod) throws DerivedException;
    
    public void setTeamOdgovara(UUID tim_Id, UUID redoslijed_Id, Integer bodovi, String kod) throws DerivedException;

    public Object findByIgra(String code);

    public ChoosingTeam findNext(UUID id, int brojAlbuma);

    public Optional<Tim> findById(UUID tim_id);
}
