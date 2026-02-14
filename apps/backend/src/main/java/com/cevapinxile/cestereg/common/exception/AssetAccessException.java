/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 *
 * @author denijal
 */

public class AssetAccessException extends DerivedException {

    public enum Reason {
        NOT_FOUND,
        UNREADABLE
    }

    private final Reason reason;

    public AssetAccessException(Reason reason, String message) {
        super(
            reason == Reason.NOT_FOUND ? 404 : 503,
            reason == Reason.NOT_FOUND ? "007" : "008",
            reason == Reason.NOT_FOUND ? "Asset Not Found" : "Asset Unavailable",
            message
        );
        this.reason = reason;
    }
}
