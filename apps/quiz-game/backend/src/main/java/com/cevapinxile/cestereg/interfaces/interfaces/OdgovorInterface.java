/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.interfaces.interfaces;

import com.cevapinxile.cestereg.entities.Odgovor;
import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.models.requests.AnswerRequest;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface OdgovorInterface {
    
    public long findSeek(LocalDateTime start_timestamp, UUID red_id);
    
    public Odgovor[] getLastInterrupts(LocalDateTime start_timestamp, UUID red_id);
    
    public void interrupt(String room_code, UUID tim_id) throws DerivedException;

    public void resolve_error(UUID zadnji, String room_code) throws DerivedException;

    public void answer(AnswerRequest ar, String room_code) throws DerivedException;

    public UUID findCorrectAnswer(UUID id, String code) throws DerivedException;

    public void previous_scenario(int scenario, String room_code) throws DerivedException;
    
}
