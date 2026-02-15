/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal
 * Thrown when an asset (audio/image) cannot be loaded from storage.
 *
 * <p>The {@link Reason} determines whether the failure is treated as:</p>
 * <ul>
 *   <li>{@link Reason#NOT_FOUND} → HTTP 404 (missing asset)</li>
 *   <li>{@link Reason#UNREADABLE} → HTTP 503 (asset exists but cannot be read / storage unavailable)</li>
 * </ul>
 *
 * <p>Typical throw sites:</p>
 * <ul>
 *   <li>{@code LocalAssetGateway.readSnippetMp3(...)} when snippet MP3 is missing/unreadable</li>
 *   <li>{@code LocalAssetGateway.readAnswerMp3(...)} when answer MP3 is missing/unreadable</li>
 * </ul>
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
