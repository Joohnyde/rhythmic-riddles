/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.controllers;

import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.interfaces.interfaces.OdgovorInterface;
import com.cevapinxile.cestereg.models.requests.AnswerRequest;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
@RequestMapping("odgovor")
@CrossOrigin(origins = "*")
public class OdgovorController {
    
    @Autowired
    private OdgovorInterface odgovor_interface;
    
    @PostMapping("interrupt")
    public ResponseEntity<?> interrupt(@RequestParam(name = "tim", required = false) UUID tim_id, @RequestHeader("ROOM_CODE") String room_code){
        try {
            odgovor_interface.interrupt(room_code, tim_id);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(OdgovorController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(OdgovorController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
    @PutMapping("continue")
    public ResponseEntity<?> resolve_error(@RequestParam("zadnji") UUID zadnji, @RequestHeader("ROOM_CODE") String room_code){
        try {
            odgovor_interface.resolve_error(zadnji, room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(OdgovorController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(OdgovorController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("answer")
    public ResponseEntity<?> resolve_error(@RequestBody AnswerRequest ar, @RequestHeader("ROOM_CODE") String room_code){
        try {
            odgovor_interface.answer(ar, room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(OdgovorController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(OdgovorController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
    @PutMapping("previous_scenario")
    public ResponseEntity<?> previous_scenario(@RequestParam("scenario") int scenario, @RequestHeader("ROOM_CODE") String room_code){
        try {
            odgovor_interface.previous_scenario(scenario, room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(OdgovorController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(OdgovorController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
    
}
