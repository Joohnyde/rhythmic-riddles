/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.gateway;

/**
 *
 * @author denijal
 */
public interface BroadcastGateway {

    void toTv(String roomCode, String payload);

    void toAdmin(String roomCode, String payload);

    default void broadcast(String roomCode, String payload) {
        toTv(roomCode, payload);
        toAdmin(roomCode, payload);
    }
}
