/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.controllers;

import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.interfaces.interfaces.TimInterface;
import com.cevapinxile.cestereg.models.requests.CreateTimRequest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("tim")
@CrossOrigin(origins = "*")
public class TimController {
    
    @Autowired
    private TimInterface tim_interface;
    
    @PostMapping
    public ResponseEntity<?> createTim(@RequestBody CreateTimRequest ctr, @RequestHeader("ROOM_CODE") String room_code){
        try {
            return ResponseEntity.ok(tim_interface.createTim(ctr, room_code));
        } catch (DerivedException ex) {
            Logger.getLogger(TimController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(TimController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
     
    @DeleteMapping
    public ResponseEntity<?> kickTim(@RequestParam("tim_id") String tim_id, @RequestHeader("ROOM_CODE") String room_code){
        try {
            tim_interface.kick(tim_id, room_code);
            return ResponseEntity.ok().build();
        } catch (DerivedException ex) {
            Logger.getLogger(TimController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(TimController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
}
