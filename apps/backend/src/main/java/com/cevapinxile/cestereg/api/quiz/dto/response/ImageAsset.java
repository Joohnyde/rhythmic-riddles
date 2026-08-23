/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

/**
 * @author denijal
 */
/**
 * Represents an image asset together with its MIME type.
 *
 * @param bytes the raw image bytes
 * @param mimeType the MIME type of the image
 */
public record ImageAsset(byte[] bytes, String mimeType) {}
