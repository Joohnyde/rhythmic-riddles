/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.services;

import com.cevapinxile.cestereg.entities.Igra;
import com.cevapinxile.cestereg.entities.Redoslijed;
import com.cevapinxile.cestereg.exceptions.AppNotRegisteredException;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.exceptions.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.exceptions.WrongGameStateException;
import com.cevapinxile.cestereg.interfaces.interfaces.IgraInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.KategorijaInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.RedoslijedInterface;
import com.cevapinxile.cestereg.interfaces.repositories.IgraRepository;
import com.cevapinxile.cestereg.interfaces.repositories.OdgovorRepository;
import com.cevapinxile.cestereg.interfaces.repositories.RedoslijedRepository;
import com.cevapinxile.cestereg.websockets.Broadcaster;
import com.cevapinxile.cestereg.websockets.SessionRegistry;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 *
 * @author denijal
 */
@Service
public class RedoslijedService implements RedoslijedInterface {
    
    @Autowired
    private RedoslijedRepository redoslijed_repository;
    
    @Autowired
    private OdgovorRepository odgovor_repository;
    
    @Autowired
    private IgraInterface igra_interface;
    
    @Autowired
    private KategorijaInterface kategorija_interface;
    
    @Autowired
    private SessionRegistry sockets;
    
    @Autowired
    private Broadcaster broadcaster;

    @Override
    @Transactional
    public void refresh(UUID zadnja, String room_code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_interface.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        Optional<Redoslijed> maybe_zadnja = redoslijed_repository.findById(zadnja);
        if(maybe_zadnja.isEmpty()) throw new InvalidReferencedObjectException("Order with id "+zadnja+" does not exist");
        
        //Jesu li obe prisutne
        if(!sockets.areBothPresent(room_code)) throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        
        LocalDateTime now = LocalDateTime.now();
        odgovor_repository.resolveError(zadnja, now);
        Redoslijed zadnja_pjesma = maybe_zadnja.get();
        zadnja_pjesma.setVremePocetka(now);
        redoslijed_repository.saveAndFlush(zadnja_pjesma);
        
        //Broadcast etw
        broadcaster.broadcast(room_code, "{\"type\":\"song_repeat\",\"remaining\":"+zadnja_pjesma.getNumeraId().getPjesmaId().getTrajanje()+"}");
        
    }
    
    @Override
    @Transactional
    public void reveal(UUID zadnja, String room_code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_interface.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        Optional<Redoslijed> maybe_zadnja = redoslijed_repository.findById(zadnja);
        if(maybe_zadnja.isEmpty()) throw new InvalidReferencedObjectException("Order with id "+zadnja+" does not exist");
        
        //Jesu li obe prisutne
        if(!sockets.areBothPresent(room_code)) throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        
        LocalDateTime now = LocalDateTime.now();
        odgovor_repository.resolveError(zadnja, now);
        Redoslijed zadnja_pjesma = maybe_zadnja.get();
        zadnja_pjesma.setVremeKraja(now);
        redoslijed_repository.saveAndFlush(zadnja_pjesma);
        
        //Razmisli treba li broadcast
        
        broadcaster.broadcast(room_code, "{\"type\":\"song_reveal\"}");
    }

    @Override
    @Transactional
    public void progress(String room_code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_interface.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        //Jesu li obe prisutne
        if(!sockets.areBothPresent(room_code)) throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        
        //Uradi magiju
        
        /*this.pesma_id = mes.pesma;
          this.pitanje = mes.pitanje;
          this.odgovor = mes.odgovor;
          this.seek = 0;
          this.remaining = mes.remaining;
          this.answer_duration = mes.answer_duration;
          this.prev_scenario = -1;
          this.zadnja_numera = mes.zadnji;
          this.scenario = 2;
          this.tim = null;
          this.prekid_id = null;*/
        
        
        Redoslijed zadnja = redoslijed_repository.findZadnja(igra.getId());
        LocalDateTime now = LocalDateTime.now();
        odgovor_repository.resolveError(zadnja.getId(), now);
        
        HashMap<String, Object> json = new HashMap<>();
        Optional<Redoslijed> maybe_sledeca = redoslijed_repository.getNext(igra.getId());
        if(maybe_sledeca.isPresent()){
            Redoslijed sledeca = maybe_sledeca.get();
            sledeca.setVremePocetka(LocalDateTime.now());
            redoslijed_repository.saveAndFlush(sledeca);
            
            IgraService.getDefault(sledeca, json);
            json.put("remaining", sledeca.getNumeraId().getPjesmaId().getTrajanje()); //Najvjerovatnije ne treba!
            json.put("type", "song_next");
            
            broadcaster.broadcast(room_code, new ObjectMapper().writeValueAsString(json));
            return;
        }
        
        int next_state = kategorija_interface.finishAndNext(igra);
        igra_interface.changeState(next_state, room_code);
    }
    
}
