/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.services;

import com.cevapinxile.cestereg.entities.Igra;
import com.cevapinxile.cestereg.entities.Tim;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.exceptions.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.exceptions.MissingArgumentException;
import com.cevapinxile.cestereg.exceptions.WrongGameStateException;
import com.cevapinxile.cestereg.interfaces.interfaces.TimInterface;
import com.cevapinxile.cestereg.interfaces.repositories.DugmeRepository;
import com.cevapinxile.cestereg.interfaces.repositories.IgraRepository;
import com.cevapinxile.cestereg.interfaces.repositories.TimRepository;
import com.cevapinxile.cestereg.models.requests.CreateTimRequest;
import com.cevapinxile.cestereg.models.responses.ChoosingTeam;
import com.cevapinxile.cestereg.models.responses.CreateTimResponse;
import com.cevapinxile.cestereg.models.responses.TeamScoreCache;
import com.cevapinxile.cestereg.models.responses.TeamScoreProjection;
import com.cevapinxile.cestereg.websockets.Broadcaster;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 *
 * @author denijal
 */
@Service
public class TimService implements TimInterface{
    
    @Autowired
    private Broadcaster bc;
    
    @Autowired
    private TimRepository tim_repository;
    
    @Autowired
    private IgraRepository igra_repository;
    
    @Autowired
    private DugmeRepository dugme_repository;
    
    private final HashMap<String, TeamScoreCache> points = new HashMap<>();
    
    @Override
    public CreateTimResponse createTim(CreateTimRequest ctr, String room_code) throws DerivedException {
        //Sanity checks - Ime postoji/validno itd
        if(StringUtils.isBlank(ctr.ime()) || StringUtils.isBlank(ctr.slika())) throw new MissingArgumentException("The request's body is missing a name and/or a picture");
        
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        if(maybe_igra.get().getStatus() != 0) throw new WrongGameStateException("Game with code "+room_code+" already started");
        
        if(dugme_repository.findById(ctr.dugme()).isEmpty()) throw new InvalidReferencedObjectException("Button with id "+ctr.dugme()+" does not exist");
        
        Tim tim = new Tim(ctr, maybe_igra.get().getId());
        tim_repository.saveAndFlush(tim);
        
        CreateTimResponse response = new CreateTimResponse(tim.getId(), tim.getNaziv(), tim.getSlika());
        bc.sendToTv(room_code, "{\"type\":\"new_team\",\"team\":"+new ObjectMapper().writeValueAsString(response)+"}");
        return response;
    }

    @Override
    public void kick(String tim_id, String room_code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        if(maybe_igra.get().getStatus() != 0) throw new WrongGameStateException("Game with code "+room_code+" already started");
        
        Optional<Tim> maybe_tim = tim_repository.findById(UUID.fromString(tim_id));
        if(maybe_tim.isEmpty()) throw new InvalidReferencedObjectException("Team with id "+tim_id+" does not exist");
        
        Tim tim = maybe_tim.get();
        tim_repository.delete(tim);
        tim_repository.flush();
        
        bc.sendToTv(room_code, "{\"type\":\"kick_team\",\"uuid\":\""+tim.getId()+"\"}");
    }    
    
    private void addToCache(String kod) throws InvalidReferencedObjectException{
        if(!points.containsKey(kod)){
            List<TeamScoreProjection> teamScores = tim_repository.getTeamScores(kod);
            if(teamScores == null) throw new InvalidReferencedObjectException("Game with code "+kod+" could not be found");
            TeamScoreCache teamScoreCache = new TeamScoreCache(teamScores);
            points.put(kod, teamScoreCache);
        }
    }

    @Override
    public Object getTeamScores(String kod) throws DerivedException {
        addToCache(kod);
        return points.get(kod).getScores();            
    }

    @Override
    public Integer getTeamPoints(UUID tim_Id, String kod) throws DerivedException {
        addToCache(kod);
        return points.get(kod).getBodovi(tim_Id);
    }

    @Override
    public void setTeamOdgovara(UUID tim_Id, UUID redoslijed_Id, Integer bodovi, String kod) throws DerivedException {
        addToCache(kod);
        points.get(kod).setOdgovarao(tim_Id,redoslijed_Id, bodovi);
    }

    @Override
    public List<CreateTimResponse> findByIgra(String code) {
        return tim_repository.findByIgra(code);
    }

    @Override
    public ChoosingTeam findNext(UUID id, int brojAlbuma) {
        return tim_repository.findNext(id, brojAlbuma);
    }

    @Override
    public Optional<Tim> findById(UUID tim_id) {
        return tim_repository.findById(tim_id);
    }
}
