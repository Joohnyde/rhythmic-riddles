/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal
 */
public class InternalServerErrorException extends DerivedException {

  public InternalServerErrorException() {
    super(500, "999", "Internal Server Error", "Unexpected internal error");
  }
}
