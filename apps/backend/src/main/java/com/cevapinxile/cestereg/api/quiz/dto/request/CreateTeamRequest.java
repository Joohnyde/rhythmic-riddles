/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.request;


/**
 *
 * @author denijal
 */
public record CreateTeamRequest(
    String name,
    String buttonCode,
    String image
) {}
