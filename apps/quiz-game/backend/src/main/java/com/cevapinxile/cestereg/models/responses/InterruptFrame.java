/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.models.responses;

import java.time.LocalDateTime;

/**
 *
 * @author denijal
 */
public class InterruptFrame {
    
    public LocalDateTime start, end;

    public InterruptFrame(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }
    
    
}
