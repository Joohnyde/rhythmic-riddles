/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.services;

import com.cevapinxile.cestereg.entities.Igra;
import com.cevapinxile.cestereg.entities.Kategorija;
import com.cevapinxile.cestereg.entities.Numera;
import com.cevapinxile.cestereg.entities.Redoslijed;
import com.cevapinxile.cestereg.entities.Tim;
import com.cevapinxile.cestereg.exceptions.AppNotRegisteredException;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.exceptions.InvalidArgumentException;
import com.cevapinxile.cestereg.exceptions.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.exceptions.MissingArgumentException;
import com.cevapinxile.cestereg.exceptions.WrongGameStateException;
import com.cevapinxile.cestereg.interfaces.interfaces.IgraInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.KategorijaInterface;
import com.cevapinxile.cestereg.interfaces.repositories.KategorijaRepository;
import com.cevapinxile.cestereg.interfaces.repositories.RedoslijedRepository;
import com.cevapinxile.cestereg.interfaces.repositories.TimRepository;
import com.cevapinxile.cestereg.models.requests.PickAlbumRequest;
import com.cevapinxile.cestereg.models.responses.LastKategorija;
import com.cevapinxile.cestereg.websockets.Broadcaster;
import com.cevapinxile.cestereg.websockets.SessionRegistry;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 *
 * @author denijal
 */
@Service
public class KategorijaService implements KategorijaInterface {

    @Autowired
    private IgraInterface igra_interface;
    
    @Autowired
    private KategorijaRepository kategorija_repository;
    
    @Autowired
    private RedoslijedRepository redoslijed_repository;
    
    @Autowired
    private TimRepository tim_repository;
    
    @Autowired
    private Broadcaster broadcaster;
    
    @Autowired
    private SessionRegistry sockets;
    
    @Override
    public LastKategorija pick(PickAlbumRequest par, String room_code) throws DerivedException {
        if(par == null) throw new MissingArgumentException("The request's body is missing");
        UUID kategorija_id = par.kategorija_id();
        UUID tim_id = par.tim_id();
        if(kategorija_id == null) throw new MissingArgumentException("The request's body is missing category_id");
        
        Optional<Kategorija> maybe_kategorija = kategorija_repository.findById(kategorija_id);
        if(maybe_kategorija.isEmpty()) throw new InvalidReferencedObjectException("Category with with id "+kategorija_id+" does not exist");
        
        Tim tim = null;
        if(tim_id != null){
            Optional<Tim> maybe_tim = tim_repository.findById(tim_id);
            if(maybe_tim.isEmpty()) throw new InvalidReferencedObjectException("Team with with id "+tim_id+" does not exist");
            tim = maybe_tim.get();
            if(!tim.getIgraId().getKod().equals(room_code)) throw new InvalidArgumentException("Room code "+room_code+" isn't consistent with the provided team");
        }
        
        Kategorija kategorija = maybe_kategorija.get();
        if(!kategorija.getIgraId().getKod().equals(room_code)) throw new InvalidArgumentException("Room code "+room_code+" isn't consistent with the category");
        if(kategorija.getIgraId().getStatus() != 1) throw new WrongGameStateException("Game "+room_code+" doesn't choose albums now");
        
        kategorija.setTimBirao(tim);
        kategorija.setRbr(kategorija_repository.getNextID(kategorija.getIgraId().getId()));
        
         //Provera da li je TV prisutan
        if(!sockets.isTvPresent(room_code)) throw new AppNotRegisteredException("TV app has to be connected to proceed");
        
        kategorija_repository.saveAndFlush(kategorija);
        LastKategorija result = new LastKategorija(kategorija);
        broadcaster.sendToTv(room_code, "{\"type\":\"album_picked\",\"selected\":"+new ObjectMapper().writeValueAsString(result)+"}");
        return result;
    }

    @Override
    public void start(UUID kategorija_id, String room_code) throws DerivedException {
        //Sanity checks
        Optional<Kategorija> maybe_kategorija = kategorija_repository.findById(kategorija_id);
        if(maybe_kategorija.isEmpty()) throw new InvalidReferencedObjectException("Category with with id "+kategorija_id+" does not exist");
        
        Kategorija kategorija = maybe_kategorija.get();
        if(!kategorija.getIgraId().getKod().equals(room_code)) throw new InvalidArgumentException("Room code "+room_code+" isn't consistent with the category");
        
        Igra igra = igra_interface.isChangeStateLegal(2, room_code);
        int broj_pjesama = igra.getBrojPjesama();
        List<Numera> numere = kategorija.getAlbumId().getNumeraList();
        if(numere.size() < broj_pjesama){
            //WTF
            throw new InvalidArgumentException("Kategorija nema toliko numera");
        }
        
        //Random biramo redosled X pjesama
        Collections.shuffle(numere);    
        AtomicInteger index = new AtomicInteger(0);
        List<Redoslijed> redoslijed = numere.subList(0, Math.min(broj_pjesama, numere.size())).stream().map(elem -> new Redoslijed(kategorija, elem, index.incrementAndGet())).toList();
        redoslijed.getFirst().setVremePocetka(LocalDateTime.now());
        redoslijed_repository.saveAllAndFlush(redoslijed);
        
        igra_interface.changeState(2, room_code); //Neoptimizovano mozes samo da broadcastujes
    }

    @Override
    @Modifying
    public int finishAndNext(Igra igra) throws DerivedException {
        LastKategorija lk = kategorija_repository.findLast(igra.getId());
        Optional<Kategorija> maybe_kategorija = kategorija_repository.findById(lk.kategorija);
        if(maybe_kategorija.isEmpty()) throw new InvalidReferencedObjectException("Category with with id "+lk.kategorija+" does not exist");
        Kategorija last = maybe_kategorija.get();
        last.setGotovo(true);
        kategorija_repository.saveAndFlush(last);
        if(last.getRbr() == igra.getBrojAlbuma()) return 3;
        return 1;
    }
    
}
