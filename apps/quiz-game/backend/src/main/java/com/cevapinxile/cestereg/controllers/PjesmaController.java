/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.controllers;

import com.cevapinxile.cestereg.interfaces.interfaces.PjesmaInterface;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author denijal
 */
@RestController
@RequestMapping("pjesma")
@CrossOrigin(origins = "*")
public class PjesmaController {
    
    @Autowired
    private PjesmaInterface pjesma_interface;
    
    @GetMapping(value = "play", produces = "audio/mpeg")
    public ResponseEntity<?> play(@RequestParam("id") UUID pjesma_id){
        try {
            byte[] content = pjesma_interface.play(pjesma_id);
            return ResponseEntity.ok()
                .header("Accept-Ranges", "bytes")
                .body(content);
        }catch (IOException ex){
            Logger.getLogger(PjesmaController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }     
    }
    
    @GetMapping("reveal")
    public ResponseEntity<?> reveal(@RequestParam("id") UUID pjesma_id){
        try {
            return ResponseEntity.ok(pjesma_interface.reveal(pjesma_id));
        }catch (IOException ex){
            Logger.getLogger(PjesmaController.class.getName()).log(Level.WARNING, "Unforseen error", ex);
            return ResponseEntity.status(500).build();
        }     
    }
    
}
