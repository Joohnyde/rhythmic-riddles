/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.websocket;

/**
 *
 * @author denijal
 */
public enum ClientType {
    ADMIN, TV;

    public int index() {
        return this == ADMIN ? 0 : 1;
    }

    public static ClientType fromSocketPosition(int pos) {
        return switch (pos) {
            case 0 -> ADMIN;
            case 1 -> TV;
            default -> throw new IllegalArgumentException("Invalid SOCKET_POSITION: " + pos);
        };
    }
}
