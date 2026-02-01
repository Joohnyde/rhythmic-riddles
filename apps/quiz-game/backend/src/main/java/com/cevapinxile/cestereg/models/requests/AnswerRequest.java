/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.models.requests;

import java.util.UUID;

/**
 *
 * @author denijal
 */
public record AnswerRequest (
        //UUID odgovor
        UUID odgovor_id,
        boolean correct
        ){
}
