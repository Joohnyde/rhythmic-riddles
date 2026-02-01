/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.services;

import com.cevapinxile.cestereg.entities.Igra;
import com.cevapinxile.cestereg.entities.Odgovor;
import com.cevapinxile.cestereg.entities.Redoslijed;
import com.cevapinxile.cestereg.entities.Tim;
import com.cevapinxile.cestereg.exceptions.AppNotRegisteredException;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.exceptions.GuessNotAllowedException;
import com.cevapinxile.cestereg.exceptions.InvalidArgumentException;
import com.cevapinxile.cestereg.exceptions.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.exceptions.UnauthorizedException;
import com.cevapinxile.cestereg.exceptions.WrongGameStateException;
import com.cevapinxile.cestereg.interfaces.interfaces.OdgovorInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.TimInterface;
import com.cevapinxile.cestereg.interfaces.repositories.IgraRepository;
import com.cevapinxile.cestereg.interfaces.repositories.OdgovorRepository;
import com.cevapinxile.cestereg.interfaces.repositories.RedoslijedRepository;
import com.cevapinxile.cestereg.models.requests.AnswerRequest;
import com.cevapinxile.cestereg.models.responses.InterruptFrame;
import com.cevapinxile.cestereg.websockets.Broadcaster;
import com.cevapinxile.cestereg.websockets.SessionRegistry;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author denijal
 */
@Service
public class OdgovorService implements OdgovorInterface{

    @Autowired
    private OdgovorRepository odgovor_repository;
    
    @Autowired
    private IgraRepository igra_repository;
    
    @Autowired
    private TimInterface tim_interface;
    
    @Autowired
    private RedoslijedRepository redoslijed_repository;
    
    @Autowired
    private Broadcaster broadcaster;
    
    @Autowired
    private SessionRegistry sockets;
    
    @Override
    public long findSeek(LocalDateTime start_timestamp, UUID red_id) {
        List<InterruptFrame> interrupts = odgovor_repository.findInterrupts(start_timestamp, red_id);
        if(interrupts == null) interrupts = new ArrayList<>();
        if(interrupts.isEmpty() || interrupts.getLast().end != null){
            interrupts.add(new InterruptFrame(LocalDateTime.now(), null));
        }
        
        LocalDateTime end = start_timestamp;
        long res = 0;
        for(InterruptFrame interrupt : interrupts){
            res +=  Duration.between(end, interrupt.start).toMillis() ;
            end = interrupt.end;
        }
        return res;
    }

    @Override
    public Odgovor[] getLastInterrupts(LocalDateTime start_timestamp, UUID red_id) {
        return new Odgovor[]{
            odgovor_repository.findLastAnswer(start_timestamp, red_id),
            odgovor_repository.findLastPause(start_timestamp, red_id)
        };
        
    }

    @Override
    @Transactional
    public void interrupt(String room_code, UUID tim_id) throws DerivedException{
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        Redoslijed zadnja = redoslijed_repository.findZadnja(igra.getId());
        Tim tim = null;
        
        if(tim_id != null){
            Optional<Tim> maybe_tim = tim_interface.findById(tim_id);
            if(maybe_tim.isEmpty()) throw new InvalidReferencedObjectException("Team with id"+tim_id+" does not exist");
            
            tim = maybe_tim.get();
            if(!tim.getIgraId().getId().equals(igra.getId())) throw new UnauthorizedException("Team with id "+tim_id+" isn't part of the game "+room_code);
            
            double seek = findSeek(zadnja.getVremePocetka(), zadnja.getId()) / 1000.0;
            double remaining = zadnja.getNumeraId().getPjesmaId().getTrajanje() - seek;
            
            List<Odgovor> odgovorList = zadnja.getOdgovorList();
            if(odgovorList != null){
                for(Odgovor o : odgovorList){
                    if(o.getTimId() != null && o.getTimId().getId().compareTo(tim_id) == 0) 
                        throw new GuessNotAllowedException("Team with id "+tim_id+" already made a guess for this song");
                }
            }
            
            if(zadnja.getVremeKraja() != null || remaining < 0) throw new GuessNotAllowedException("Song "+zadnja.getNumeraId().getPjesmaId().getId()+" is no longer playing");
            
            Odgovor[] interrupts = getLastInterrupts(zadnja.getVremePocetka(), zadnja.getId());
            Odgovor odgovor = interrupts[0];
            Odgovor pauza = interrupts[1];
            if(odgovor != null && odgovor.getTacan() == null) throw new GuessNotAllowedException("Someone's already guessing");
            if(pauza != null && pauza.getVremeResen() != null) throw new GuessNotAllowedException("The game is paused");
            
        }
        Odgovor novi_interrupt = new Odgovor(UUID.randomUUID());
        novi_interrupt.setRedoslijedId(zadnja);
        novi_interrupt.setTimId(tim);
        novi_interrupt.setVremeStigao(LocalDateTime.now());
        odgovor_repository.saveAndFlush(novi_interrupt);
        
        broadcaster.broadcast(room_code, "{\"type\":\"pause\",\"team\":\""+(tim==null?"null":tim.getId())+"\",\"prekid_id\":\""+novi_interrupt.getId()+"\"}");
    }

    @Override
    @Transactional
    public void resolve_error(UUID zadnji, String room_code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        Optional<Redoslijed> maybe_zadnja = redoslijed_repository.findById(zadnji);
        if(maybe_zadnja.isEmpty()) throw new InvalidReferencedObjectException("Order with id "+zadnji+" does not exist");
        
        //Jesu li obe prisutne
        if(!sockets.areBothPresent(room_code)) throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        
        Integer previousScenario = odgovor_repository.getPreviousScenario(zadnji);
        odgovor_repository.resolveError(zadnji, LocalDateTime.now());
        broadcaster.broadcast(room_code, "{\"type\":\"error_solved\",\"prev\":"+previousScenario+"}");
    }

    @Override
    @Transactional
    public void answer(AnswerRequest ar, String room_code) throws DerivedException {
        UUID odgovor_id = ar.odgovor_id();
        boolean correct = ar.correct();
        
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        //Jesu li obe prisutne
        if(!sockets.areBothPresent(room_code)) throw new AppNotRegisteredException("Both apps need to be present in order to continue");
        
        Optional<Odgovor> maybe_odgovor = odgovor_repository.findById(odgovor_id);
        if(maybe_odgovor.isEmpty()) throw new InvalidReferencedObjectException("Order with id "+odgovor_id+" does not exist");
        
        Odgovor odgovor = maybe_odgovor.get();
        if(!odgovor.getTimId().getIgraId().getKod().equals(room_code)) throw new InvalidArgumentException("Room code "+room_code+" isn't consistent with the answer");
        if(odgovor.getTacan() != null || odgovor.getVremeResen() != null) throw new GuessNotAllowedException("That guess was already answered");
        
        LocalDateTime resolve_time = LocalDateTime.now();
        
        
        Integer new_points = tim_interface.getTeamPoints(odgovor.getTimId().getId(), room_code) + (correct ? 30 : -10);
        odgovor.setVremeResen(resolve_time);
        odgovor.setTacan(correct);
        odgovor.setBodovi(new_points);
        odgovor_repository.resolveError(odgovor.getRedoslijedId().getId(), resolve_time);
        odgovor_repository.save(odgovor);
        
        if(correct){
            Redoslijed redoslijedId = odgovor.getRedoslijedId();
            redoslijedId.setVremeKraja(resolve_time);
            redoslijed_repository.saveAndFlush(redoslijedId);
        }
        
        tim_interface.setTeamOdgovara(odgovor.getTimId().getId(), odgovor.getRedoslijedId().getId(), new_points, room_code);
        broadcaster.broadcast(room_code, "{\"type\":\"answer\",\"team_id\":\""+odgovor.getTimId().getId()+"\",\"redoslijed_id\":\""+odgovor.getRedoslijedId().getId()+"\",\"correct\":"+correct+"}");
    }

    @Override
    public UUID findCorrectAnswer(UUID id, String code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_repository.findByKod(code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        return odgovor_repository.findCorrectAnswer(id);
    }

    @Override
    @Transactional
    public void previous_scenario(int scenario, String room_code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if(maybe_igra.isEmpty()) throw new InvalidReferencedObjectException("Game with code "+room_code+" does not exist");
        
        Igra igra = maybe_igra.get();
        int status = igra.getStatus();
        if(status != 2) throw new WrongGameStateException("The game is not in the song listening stage");
        
        
        Redoslijed zadnja = redoslijed_repository.findZadnja(igra.getId());
        if(zadnja != null){
            Odgovor pauza = odgovor_repository.findLastPause(zadnja.getVremePocetka(), zadnja.getId());
            if(pauza != null){
                pauza.setBodovi(scenario);
                odgovor_repository.saveAndFlush(pauza);
            }  
        }
        //Nadji gresku i stavi bodove kao secnario
        
    }
    
}
