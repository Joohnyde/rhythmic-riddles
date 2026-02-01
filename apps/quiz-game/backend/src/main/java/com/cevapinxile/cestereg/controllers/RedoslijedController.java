/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.controllers;

import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.interfaces.interfaces.RedoslijedInterface;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author denijal
 */
@RestController
@RequestMapping("redoslijed")
@CrossOrigin(origins = "*")
public class RedoslijedController {
    
    @Autowired
    private RedoslijedInterface redoslijed_interface;
    
    @PostMapping("refresh")
    public ResponseEntity<?> refresh(@RequestParam("zadnji") UUID zadnji, @RequestHeader("ROOM_CODE") String room_code){
        try {
            redoslijed_interface.refresh(zadnji, room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(RedoslijedController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(RedoslijedController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("reveal")
    public ResponseEntity<?> reveal(@RequestParam("zadnji") UUID zadnji, @RequestHeader("ROOM_CODE") String room_code){
        try {
            redoslijed_interface.reveal(zadnji, room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(RedoslijedController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(RedoslijedController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("next")
    public ResponseEntity<?> next(@RequestHeader("ROOM_CODE") String room_code){
        try {
            redoslijed_interface.progress(room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(RedoslijedController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(RedoslijedController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
}
