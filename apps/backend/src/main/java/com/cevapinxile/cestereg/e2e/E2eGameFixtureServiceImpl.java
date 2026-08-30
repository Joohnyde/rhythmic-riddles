package com.cevapinxile.cestereg.e2e;

import com.cevapinxile.cestereg.common.exception.E2eGameFixtureValidationException;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.AlbumRepository;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import com.cevapinxile.cestereg.persistence.repository.TrackRepository;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class E2eGameFixtureServiceImpl implements E2eGameFixtureService {

  @Autowired private GameRepository gameRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private TeamRepository teamRepository;

  @Autowired private TrackRepository trackRepository;

  @Autowired private AlbumRepository albumRepository;

  @Autowired private ScheduleRepository scheduleRepository;

  @Autowired private InterruptRepository interruptRepository;

  @Autowired private E2eGameFixtureValidator fixtureValidator;

  @Override
  @Transactional
  public void resetRuntimeState(final String roomCode) {
    gameRepository.deleteByCode(roomCode);
  }

  @Override
  @Transactional
  public void createFixture(final E2eGameFixtureRequest request)
      throws E2eGameFixtureValidationException {
    fixtureValidator.validateOrThrow(request);

    final GameEntity newGame = gameRepository.saveAndFlush(new GameEntity(request));

    final Map<UUID, TeamEntity> persistedTeams =
        teamRepository
            .saveAllAndFlush(
                request.teams().stream().map(team -> new TeamEntity(team, newGame)).toList())
            .stream()
            .collect(Collectors.toMap(TeamEntity::getId, Function.identity()));

    request
        .categories()
        .forEach(
            category -> {
              final CategoryEntity newCategory = new CategoryEntity(category, newGame);

              final AlbumEntity newAlbum = albumRepository.saveAndFlush(newCategory.getAlbumId());
              categoryRepository.saveAndFlush(newCategory);

              category
                  .album()
                  .tracks()
                  .forEach(
                      track -> {
                        final TrackEntity newTrack =
                            trackRepository.saveAndFlush(new TrackEntity(track, newAlbum));
                        final E2eGameFixtureRequest.Schedule schedule = track.schedule();
                        if (schedule != null) {
                          final ScheduleEntity newSchedule =
                              scheduleRepository.saveAndFlush(
                                  new ScheduleEntity(schedule, newCategory, newTrack));
                          if (schedule.interrupts() != null) {
                            interruptRepository.saveAllAndFlush(
                                schedule.interrupts().stream()
                                    .map(
                                        interrupt ->
                                            new InterruptEntity(
                                                interrupt,
                                                newSchedule,
                                                persistedTeams.get(interrupt.teamId())))
                                    .toList());
                          }
                        }
                      });
            });
  }

  @Override
  @Transactional
  public void attachCatalog(final String roomCode, final E2eCatalogFixtureRequest request)
      throws E2eGameFixtureValidationException {
    final GameEntity game =
        gameRepository
            .findByCode(roomCode)
            .orElseThrow(
                () ->
                    new E2eGameFixtureValidationException(
                        java.util.List.of("room must exist before attaching a catalog")));
    if (game.getStage() != 0) {
      throw new E2eGameFixtureValidationException(
          java.util.List.of("catalog can only be attached while the room is in the lobby"));
    }
    if (request.categories().size() < game.getMaxAlbums()) {
      throw new E2eGameFixtureValidationException(
          java.util.List.of("catalog must contain at least maxAlbums categories"));
    }

    for (E2eGameFixtureRequest.Category category : request.categories()) {
      if (category.pickedByTeamId() != null
          || category.ordinalNumber() != null
          || Boolean.TRUE.equals(category.done())) {
        throw new E2eGameFixtureValidationException(
            java.util.List.of("attached catalog categories must be unplayed"));
      }
      if (category.album().tracks().size() < game.getMaxSongs()) {
        throw new E2eGameFixtureValidationException(
            java.util.List.of("every catalog album must contain at least maxSongs tracks"));
      }

      final CategoryEntity newCategory = new CategoryEntity(category, game);
      final AlbumEntity newAlbum = albumRepository.saveAndFlush(newCategory.getAlbumId());
      categoryRepository.saveAndFlush(newCategory);
      trackRepository.saveAllAndFlush(
          category.album().tracks().stream()
              .map(track -> new TrackEntity(track, newAlbum))
              .toList());
    }
  }
}
