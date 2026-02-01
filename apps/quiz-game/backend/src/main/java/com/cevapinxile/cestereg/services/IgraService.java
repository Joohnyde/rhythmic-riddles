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
import com.cevapinxile.cestereg.exceptions.InvalidArgumentException;
import com.cevapinxile.cestereg.exceptions.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.exceptions.WrongGameStateException;
import com.cevapinxile.cestereg.interfaces.interfaces.IgraInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.OdgovorInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.TimInterface;
import com.cevapinxile.cestereg.interfaces.repositories.IgraRepository;
import com.cevapinxile.cestereg.interfaces.repositories.KategorijaRepository;
import com.cevapinxile.cestereg.interfaces.repositories.RedoslijedRepository;
import com.cevapinxile.cestereg.interfaces.repositories.TimRepository;
import com.cevapinxile.cestereg.models.requests.CreateIgraRequest;
import com.cevapinxile.cestereg.models.responses.ChoosingTeam;
import com.cevapinxile.cestereg.models.responses.CreateTimResponse;
import com.cevapinxile.cestereg.models.responses.LastKategorija;
import com.cevapinxile.cestereg.websockets.Broadcaster;
import com.cevapinxile.cestereg.websockets.SessionRegistry;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 *
 * @author denijal
 */
@Service
public class IgraService implements IgraInterface {

    @Autowired
    private IgraRepository igra_repository;

    @Autowired
    private TimInterface tim_interface;

    @Autowired
    private KategorijaRepository kategorija_repository;

    @Autowired
    private RedoslijedRepository redoslijed_repository;

    @Autowired
    private OdgovorInterface odgovor_interface;

    @Autowired
    private Broadcaster broadcaster;

    @Autowired
    private SessionRegistry sockets;

    @Override
    public String createIgra(CreateIgraRequest cir) throws DerivedException {
        if (cir.broj_albuma() != null && cir.broj_albuma() < 0) {
            throw new InvalidArgumentException("Number of albums must be a positive integer");
        }
        if (cir.broj_pjesama() != null && cir.broj_pjesama() < 0) {
            throw new InvalidArgumentException("Number of songs must be a positive integer");
        }
        //Sanity checks, brojevi validni itd
        Igra potencijalna_igra = new Igra(cir);
        while (igra_repository.findByKod(potencijalna_igra.getKod()).isPresent()) {
            potencijalna_igra = new Igra(cir);
        }
        igra_repository.saveAndFlush(potencijalna_igra);
        return potencijalna_igra.getKod();
    }

    public static void getDefault(Redoslijed zadnja, HashMap<String, Object> json) {
        String pitanje = zadnja.getNumeraId().getAlbumId().getPitanje();
        String odgovor_string = zadnja.getNumeraId().getOdgovor();
        if (pitanje == null) {
            pitanje = "Prepoznaj ovu pjesmu!";
        }
        if (odgovor_string == null) {
            odgovor_string = zadnja.getNumeraId().getPjesmaId().toString();
        }
        json.put("pesma", zadnja.getNumeraId().getPjesmaId().getId());
        json.put("pitanje", pitanje);
        json.put("odgovor", odgovor_string);
        json.put("zadnji", zadnja.getId());
        json.put("answer_duration", zadnja.getNumeraId().getPjesmaId().getOdgovor()); //Najvjerovatnije ne treba!
    }

    @Override
    public HashMap<String, Object> contextFetch(String code) throws DerivedException{
        HashMap<String, Object> json = new HashMap<>();

        Optional<Igra> maybe_igra = igra_repository.findByKod(code);
        if (maybe_igra.isEmpty()) {
            return json;
        }

        Igra igra = maybe_igra.get();
        json.put("type", "welcome");
        switch (igra.getStatus()) {
            case 0:
                json.put("teams", tim_interface.findByIgra(code));
                json.put("stage", "lobby");
                //Dohvati listu timova
                break;
            case 1:
                LastKategorija poslednja_birana = kategorija_repository.findLast(igra.getId());
                if (poslednja_birana == null || poslednja_birana.started && poslednja_birana.rbr != igra.getBrojAlbuma()) {
                    //Poslednja je birana. Biramo sledecu ako nije kraj

                    //Izracunaj ko treba da bira
                    ChoosingTeam birac = tim_interface.findNext(igra.getId(), igra.getBrojAlbuma());
                    json.put("albums", kategorija_repository.findByIgra(igra.getId()));
                    json.put("team", birac == null ? birac : new CreateTimResponse(birac));
                } else if (!poslednja_birana.started) {
                    //Nesto je izabrano ali nije pocelo
                    json.put("selected", poslednja_birana);
                }
                //Else bi znacilo da je kraj a mi smo u stage 1. Nemoguce!

                json.put("stage", "albums");
                //Ako je poslednji izabrani gotovo = true, sledi novo biranje
                //Vrati objekte albuma (ime, slika, picked by), ko sad bira i boolean chosen=false

                //U suprotnom sam dosao u medjutrenutak kad smo izabrali a nismo poceli
                //Vrati sta je izabrano, ko je izabrao i boolean chosen=true
                //Ko sad bira se ide redom kako su dugmad
                break;
            case 2:

                json.put("stage", "songs");
                //Ako je zadnje pustena pjesma (started != null) zavrsena mi smo u medjutrenutku:
                //Prikazi odgovor na ekranu i nakon 5s posalji zahtev za dalje
                //Vrati odgovor:X, revealed: true, sve timove i slike 
                Redoslijed zadnja = redoslijed_repository.findZadnja(igra.getId());

                //Defaultna polja: pjesma_id, pitanje, odgovor, timovi, bodovi
                IgraService.getDefault(zadnja, json);
                json.put("scores", tim_interface.getTeamScores(code));

                if (zadnja.getVremeKraja() != null) {
                    //Medjutrenutak
                    json.put("revealed", true);
                    json.put("bravo", odgovor_interface.findCorrectAnswer(zadnja.getId(), code));
                    //Nadji kome bravo
                    break;
                }

                //Ako nije zavrsena onda moramo da skontamo dje smo. Izracunaj seek. 
                double seek = odgovor_interface.findSeek(zadnja.getVremePocetka(), zadnja.getId()) / 1000.0;
                double remaining = zadnja.getNumeraId().getPjesmaId().getTrajanje() - seek;

                if (remaining < 0) {
                    //Ukoliko je seek prosao pesmu - prikazi dugmad refresh i dalje
                    //Vrati odgovor:X, revealed: false, sve time i slike,
                    json.put("revealed", false);
                    break;
                }

                //Seek nije prosao pjesmu:
                json.put("seek", seek);
                json.put("remaining", remaining); //Najvjerovatnije ne treba!

                //U tabelu odgovora vidi zadnji odgovor (tim != null vreme_stigao > numera.vreme_poceo). 
                Odgovor[] interrupts = odgovor_interface.getLastInterrupts(zadnja.getVremePocetka(), zadnja.getId());

                Odgovor odgovor = interrupts[0];
                Odgovor pauza = interrupts[1];

                if (odgovor != null && odgovor.getTacan() == null) {
                    //Ako je tacan = null onda tim odgovara
                    //Vrati seek, odgovor, preostalo_vreme, tim koji odgovara, play: no, sve timove i slike
                    Tim tim = odgovor.getTimId();
                    json.put("team", new CreateTimResponse(tim.getId(), tim.getNaziv(), tim.getSlika()));
                    json.put("prekid_id", odgovor.getId());
                    break;

                }
                if (pauza != null && pauza.getVremeResen() == null) {
                    //Ako nema takvog proveri da li postoji (tim = null, vreme_stigao > numera.vreme_poceo). 
                    //Ako takvog ima onda je pauza. Prikazi continue dugmad koje radi samo ako su oba socketa tu
                    //Vrati seek, preostalo_vreme, error: yes
                    json.put("error", true); //moze samo zadnji 
                    break;

                }

                //Seek nije pronasao pesmu i nema pauza - Pesma jos ide
                //Vrati seek, preostalo_vreme, play: yes, sve timove i slike
                break;
            case 3:
                json.put("stage", "winner");
                json.put("teams", tim_interface.findByIgra(code));
                break;
        }

        return json;
    }

    @Override
    public Igra isChangeStateLegal(int stage_id, String room_code) throws DerivedException {
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if (maybe_igra.isEmpty()) {
            throw new InvalidReferencedObjectException("Game with code " + room_code + " does not exist");
        }

        Igra igra = maybe_igra.get();
        int status = igra.getStatus();

        if (stage_id < 1 || stage_id > 4) {
            throw new InvalidArgumentException("Stage id has to be a number between 1 and 3");
        }
        if (stage_id == status) {
            throw new InvalidArgumentException("Game is already in that state");
        }
        if (status == 0 && stage_id != 1) {
            throw new WrongGameStateException("This game is in lobby state. The only allowed state transition is to album selection (stage 1)");
        }
        if (status == 1 && stage_id != 2) {
            throw new WrongGameStateException("Album selection is in progress. We can only move to song listening (stage 2)");
        }
        if (status == 2 && stage_id == 0) {
            throw new WrongGameStateException("We're listening to a song. Stage has to be 1 (album selection) or 3 (finish)");
        }

        //Provera da li je TV prisutan
        if (!sockets.isTvPresent(room_code)) {
            throw new AppNotRegisteredException("TV app has to be connected to proceed");
        }
        return igra;
    }

    @Override
    public void changeState(int stage_id, String room_code) throws DerivedException {
        Igra igra = isChangeStateLegal(stage_id, room_code);
        igra.setStatus(stage_id);
        igra_repository.saveAndFlush(igra);
        broadcaster.broadcast(room_code, new ObjectMapper().writeValueAsString(contextFetch(room_code)));
    }

    @Override
    public int getState(String room_code) {
        Optional<Igra> maybe_igra = igra_repository.findByKod(room_code);
        if (maybe_igra.isEmpty()) {
            return -1;
        }
        return maybe_igra.get().getStatus();
    }

    @Override
    public Optional<Igra> findByKod(String room_code) {
        return igra_repository.findByKod(room_code);
    }

}
