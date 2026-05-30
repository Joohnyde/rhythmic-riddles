/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.e2e.service.impl;

import com.cevapinxile.cestereg.e2e.dto.E2eGameFixtureRequest;
import com.cevapinxile.cestereg.e2e.service.E2eGameFixtureService;
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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author denijal
 */
@Service
public class E2eGameFixtureServiceImpl implements E2eGameFixtureService {

    @Autowired
    private GameRepository gameRepository;
    
    @Autowired
    private CategoryRepository cateogryRepository;

    @Autowired
    private TeamRepository teamRepository;
    
    @Autowired
    private TrackRepository trackRepository;
    
    @Autowired
    private AlbumRepository albumRepository;
    
    @Autowired
    private ScheduleRepository scheduleRepository;
    
    @Autowired
    private InterruptRepository interruptRepository;

    @Override
    @Transactional
    public void resetRuntimeState(String roomCode) {
        gameRepository.findByCode(roomCode).ifPresent(gameRepository::delete);
    }

    @Override
    @Transactional
    public void createFixture(E2eGameFixtureRequest request) {
        // Create game
        GameEntity newGame = new GameEntity(request);
        gameRepository.save(newGame);

        // Create teams
        teamRepository.saveAll(
                request.teams().stream().map(team -> {
                    TeamEntity newTeam = new TeamEntity(team);
                    newTeam.setGameId(newGame);
                    return newTeam;
                }).toList());

        request.categories().forEach(category -> {
            CategoryEntity newCategory = new CategoryEntity(category);
            AlbumEntity newAlbum = new AlbumEntity(category.album());
            
            newCategory.setGameId(newGame);
            newCategory.setAlbumId(newAlbum);
            
            // Create albums
            albumRepository.save(newAlbum);
            
            // Create categories
            cateogryRepository.save(newCategory);
            
            // Create songs
            category.album().tracks().forEach(track -> {                
                TrackEntity newTrack = new TrackEntity(track);
                newTrack.setAlbumId(newAlbum);
                trackRepository.save(newTrack);
                
                E2eGameFixtureRequest.Schedule schedule = track.schedule();
                if(schedule != null){
                    // Create schedules
                    ScheduleEntity newSchedule = new ScheduleEntity(schedule);
                    newSchedule.setCategoryId(newCategory);
                    newSchedule.setTrackId(newTrack);
                    scheduleRepository.save(newSchedule);
                    
                    // Create interrupts
                    Optional.ofNullable(schedule.interrupts()).ifPresent(
                        listInterrupt -> {
                            interruptRepository.saveAll(listInterrupt.stream().map(interrupt -> {
                            InterruptEntity newInterrupt = new InterruptEntity(interrupt);
                            newInterrupt.setScheduleId(newSchedule);
                            return newInterrupt;
                            }).toList());
                        }
                    );
                }
            });
        });
    }

}
