/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.websocket;

/**
 * Represents the type of client connected to the system.
 *
 * <p>The client type determines the role and behavior of the connected entity within a session.
 * Each type corresponds to a predefined socket position used by the networking layer.
 *
 * <ul>
 *   <li>{@link #ADMIN} – Administrative client responsible for controlling the game/session.
 *   <li>{@link #TV} – Display client responsible for presenting content to players.
 * </ul>
 *
 * <p>The enum also provides utility methods to translate between client types and their
 * corresponding socket positions.
 *
 * @author denijal
 */
public enum ClientType {

  /** Administrative client responsible for controlling the game/session. */
  ADMIN,

  /** Display client responsible for presenting the game/session state. */
  TV;

  /**
   * Returns the socket index associated with this client type.
   *
   * <p>The index corresponds to the position used by the networking layer to identify the connected
   * client.
   *
   * @return the socket index for this client type
   */
  public int index() {
    return this == ADMIN ? 0 : 1;
  }

  /**
   * Resolves the {@link ClientType} associated with a given socket position.
   *
   * <p>This method converts the integer position used by the networking layer into the
   * corresponding {@code ClientType}.
   *
   * @param pos the socket position
   * @return the client type associated with the given position
   * @throws IllegalArgumentException if the position does not correspond to a valid client type
   */
  public static ClientType fromSocketPosition(int pos) {
    return switch (pos) {
      case 0 -> ADMIN;
      case 1 -> TV;
      default -> throw new IllegalArgumentException("Invalid SOCKET_POSITION: " + pos);
    };
  }
}
