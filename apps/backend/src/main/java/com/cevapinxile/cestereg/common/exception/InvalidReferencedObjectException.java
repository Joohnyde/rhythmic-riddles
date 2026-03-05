/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.common.exception;

/**
 * @author denijal Thrown when a request references an entity that does not exist.
 *     <p>This is the domain-level "not found" error (HTTP 404). It is used whenever an identifier
 *     (UUID / room code) points to a missing Game, Category, Team, Schedule/Order, Answer, etc.
 *     <p>Typical throw sites:
 *     <ul>
 *       <li>{@code GameRepository.findByCode(...)} when the room code does not exist
 *       <li>{@code CategoryServiceImpl.pickAlbum(...)} when referenced team/category is missing
 *       <li>{@code ScheduleServiceImpl.replaySong(...)} / {@code revealAnswer(...)} when schedule
 *           is missing
 *       <li>{@code InterruptServiceImpl.answer(...)} when an answer id does not exist
 *       <li>{@code TeamScoreCache.getScore(...)} when the team is not present in the cache
 *     </ul>
 */
public class InvalidReferencedObjectException extends DerivedException {

  public InvalidReferencedObjectException(String message) {
    super(404, "001", "Invalid referenced object", message);
  }
}
