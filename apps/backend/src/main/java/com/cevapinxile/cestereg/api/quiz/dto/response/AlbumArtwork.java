package com.cevapinxile.cestereg.api.quiz.dto.response;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.MissingArgumentException;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import java.util.UUID;

/**
 * Album presentation included in song snapshots and next-song events.
 *
 * @param image album UUID used by the album image endpoint
 * @param name album display name
 * @param picker selecting team's image reference, or null when Admin picked
 * @param pickerName selecting team name, or Admin when no team picked
 */
public record AlbumArtwork(UUID image, String name, String picker, String pickerName) {

  /**
   * Creates the presentation from the scheduled category.
   *
   * @param category category whose album and picker are displayed
   * @return album presentation for the category
   * @throws DerivedException if the category or its album is missing
   */
  public static AlbumArtwork fromCategory(CategoryEntity category) throws DerivedException {
    if (category == null) {
      throw new MissingArgumentException("Could not create album artwork from a null category");
    }
    final AlbumEntity album = category.getAlbumId();
    if (album == null) {
      throw new InvalidReferencedObjectException("Category doesn't reference an album");
    }
    final TeamEntity team = category.getPickedByTeamId();
    return new AlbumArtwork(
        album.getId(),
        album.getName(),
        team == null ? null : team.getImage(),
        team == null ? "Admin" : team.getName());
  }
}
