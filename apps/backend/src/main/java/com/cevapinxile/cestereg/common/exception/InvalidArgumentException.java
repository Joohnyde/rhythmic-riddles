/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 *
 * @author denijal
 */
public class InvalidArgumentException extends DerivedException {

    public InvalidArgumentException(String message) {
        super(422, "002", "Malformed argument", message);
    }
}
