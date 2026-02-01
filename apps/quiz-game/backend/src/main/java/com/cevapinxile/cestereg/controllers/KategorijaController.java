/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.controllers;

import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.interfaces.interfaces.KategorijaInterface;
import com.cevapinxile.cestereg.models.requests.PickAlbumRequest;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author denijal
 */
@RestController
@RequestMapping("kategorija")
@CrossOrigin(origins = "*")
public class KategorijaController {
    
    @Autowired
    private KategorijaInterface kategorija_interface;
    
    @PostMapping("pick")
    public ResponseEntity<?> pick(@RequestBody PickAlbumRequest par, @RequestHeader("ROOM_CODE") String room_code){
        try {
            return ResponseEntity.ok(kategorija_interface.pick(par, room_code));
        }  catch (DerivedException ex) {
            Logger.getLogger(KategorijaController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(KategorijaController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("start")
    public ResponseEntity<?> start(@RequestParam("kategorija") UUID kategorija_id, @RequestHeader("ROOM_CODE") String room_code){
        try {
            kategorija_interface.start(kategorija_id, room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(KategorijaController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(KategorijaController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
}
