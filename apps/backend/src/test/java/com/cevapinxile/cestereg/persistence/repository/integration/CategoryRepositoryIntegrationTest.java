package com.cevapinxile.cestereg.persistence.repository.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.api.quiz.dto.response.CategorySimple;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CategoryRepositoryIntegrationTest extends PostgresJpaIntegrationTest {

  @Autowired private CategoryRepository categoryRepository;
  @Autowired private JdbcTemplate jdbc;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void findNextIdStartsAtOneAndAdvancesFromHighestPickedOrdinal() {
    final UUID gameId = fixture.game("CNXT", 1, 3, 4);
    final UUID albumA = fixture.album("A");
    final UUID albumB = fixture.album("B");
    final UUID albumC = fixture.album("C");

    assertEquals(1, categoryRepository.findNextId(gameId));

    fixture.category(gameId, albumA, null, 1, false);
    fixture.category(gameId, albumB, null, 3, false);
    fixture.category(gameId, albumC, null, null, false);

    final UUID otherGame = fixture.game("CNX2", 1, 3, 4);
    fixture.category(otherGame, fixture.album("Other"), null, 99, false);

    assertEquals(4, categoryRepository.findNextId(gameId));
  }

  @Test
  void findLastCategoryReturnsHighestPickedOrdinalWithPickerAndStartedFlag() {
    final UUID gameId = fixture.game("CLST", 1, 3, 3);
    final UUID teamId = fixture.team(gameId, "Blue", "blue.png");
    final UUID firstAlbum = fixture.album("First");
    final UUID expectedAlbum = fixture.album("Latest");
    fixture.category(gameId, firstAlbum, teamId, 1, true);
    final UUID expectedCategory = fixture.category(gameId, expectedAlbum, teamId, 2, false);

    final LastCategory result = categoryRepository.findLastCategory(gameId);

    assertEquals(expectedCategory, result.getCategoryId());
    assertEquals(2, result.getOrdinalNumber());
    assertFalse(result.isStarted());
    assertEquals(teamId, result.getPickedByTeam().getId());
    assertEquals("Latest", result.getChosenCategoryPreview().title());
  }

  @Test
  void findLastCategoryIgnoresUnpickedCategoriesAndIsGameScoped() {
    final UUID gameA = fixture.game("CGA1", 1, 3, 2);
    final UUID teamA = fixture.team(gameA, "A", "a.png");
    final UUID pickedAlbum = fixture.album("Picked");
    final UUID unpickedAlbum = fixture.album("Unpicked");
    final UUID expected = fixture.category(gameA, pickedAlbum, teamA, 1, true);
    fixture.category(gameA, unpickedAlbum, null, null, false);

    final UUID gameB = fixture.game("CGB2", 1, 3, 2);
    final UUID otherAlbum = fixture.album("Other");
    fixture.category(gameB, otherAlbum, null, 99, true);

    final LastCategory result = categoryRepository.findLastCategory(gameA);

    assertEquals(expected, result.getCategoryId());
    assertTrue(result.isStarted());
    assertNull(categoryRepository.findLastCategory(fixture.game("CEMP", 1, 3, 2)));
  }

  @Test
  void findByGameIdProjectsAlbumPickerAndOrdinalWithoutCrossGameLeakage() {
    final UUID gameId = fixture.game("CPRJ", 1, 3, 3);
    final UUID teamId = fixture.team(gameId, "Picker", "picker.png");
    final UUID pickedAlbum = fixture.album("Picked album");
    final UUID openAlbum = fixture.album("Open album");
    final UUID pickedCategory = fixture.category(gameId, pickedAlbum, teamId, 2, false);
    final UUID openCategory = fixture.category(gameId, openAlbum, null, null, false);

    final UUID otherGame = fixture.game("CPR2", 1, 3, 1);
    fixture.category(otherGame, fixture.album("Other album"), null, null, false);

    final List<CategorySimple> categories = categoryRepository.findByGameId(gameId);

    assertEquals(2, categories.size());
    final CategorySimple picked =
        categories.stream()
            .filter(category -> category.getId().equals(pickedCategory))
            .findFirst()
            .orElseThrow();
    final CategorySimple open =
        categories.stream()
            .filter(category -> category.getId().equals(openCategory))
            .findFirst()
            .orElseThrow();

    assertEquals("Picked album", picked.getName());
    assertEquals(pickedAlbum + ".png", picked.getImage());
    assertEquals("picker.png", picked.getPickedByTeam());
    assertEquals(2, picked.getOrdinalNumber());

    assertEquals("Open album", open.getName());
    assertNull(open.getPickedByTeam());
    assertNull(open.getOrdinalNumber());
  }
}
