/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.gateway;

/**
 *
 * @author denijal
 */
public interface PresenceGateway {

    boolean isTvPresent(String roomCode);

    boolean isAdminPresent(String roomCode);

    default boolean areBothPresent(String roomCode){
        return isTvPresent(roomCode) && isAdminPresent(roomCode);
    }
}
