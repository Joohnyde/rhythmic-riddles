/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.controllers;

import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.interfaces.interfaces.IgraInterface;
import com.cevapinxile.cestereg.models.requests.CreateIgraRequest;
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
@RequestMapping("igra")
@CrossOrigin(origins = "*")
public class IgraController {
    
    @Autowired
    private IgraInterface igra_interface;
    
    
    @PostMapping
    public ResponseEntity<?> createIgra(@RequestBody CreateIgraRequest request){
        try {
            return ResponseEntity.ok(igra_interface.createIgra(request));
        }  catch (DerivedException ex) {
            Logger.getLogger(IgraController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(IgraController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    } 
    
    @PostMapping("changeState")
    public ResponseEntity<?> changeState(@RequestParam("stage_id") int stage_id, @RequestHeader("ROOM_CODE") String room_code){
        try {
            igra_interface.changeState(stage_id, room_code);
            return ResponseEntity.ok().build();
        }  catch (DerivedException ex) {
            Logger.getLogger(IgraController.class.getName()).log(Level.INFO, ex.toString());
            return ResponseEntity.status(ex.HTTP_CODE).body(ex.toString());
        } catch (Exception ex){
            Logger.getLogger(IgraController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }
    }
    
}
