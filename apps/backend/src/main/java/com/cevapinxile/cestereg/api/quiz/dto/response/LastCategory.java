/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import java.util.Objects;
import java.util.UUID;

/*
 * @author denijal
 */
public class LastCategory {
  private UUID categoryId;
  private CategoryPreview chosenCategoryPreview;
  private CreateTeamResponse pickedByTeam = null;
  private boolean started = false;
  // Selected-category snapshots always carry a concrete ordinal; primitive int is non-null on wire.
  private int ordinalNumber;

  public LastCategory() {}

  public LastCategory(CategoryEntity c) {
    this.categoryId = c.getId();
    this.chosenCategoryPreview =
        new CategoryPreview(c.getAlbumId().getName(), c.getAlbumId().getId().toString());
    final TeamEntity choosingTeam = c.getPickedByTeamId();
    if (choosingTeam != null) {
      this.pickedByTeam = new CreateTeamResponse(choosingTeam);
    }
    if (c.isDone() != null) {
      this.started = c.isDone();
    }
    this.ordinalNumber =
        Objects.requireNonNull(
            c.getOrdinalNumber(), "Selected category must have an ordinal number");
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(UUID categoryId) {
    this.categoryId = categoryId;
  }

  public CategoryPreview getChosenCategoryPreview() {
    return chosenCategoryPreview;
  }

  public void setChosenCategoryPreview(CategoryPreview chosenCategoryPreview) {
    this.chosenCategoryPreview = chosenCategoryPreview;
  }

  public CreateTeamResponse getPickedByTeam() {
    return pickedByTeam;
  }

  public void setPickedByTeam(CreateTeamResponse pickedByTeam) {
    this.pickedByTeam = pickedByTeam;
  }

  public boolean isStarted() {
    return started;
  }

  public void setStarted(boolean started) {
    this.started = started;
  }

  public int getOrdinalNumber() {
    return ordinalNumber;
  }

  public void setOrdinalNumber(int ordinalNumber) {
    this.ordinalNumber = ordinalNumber;
  }
}
