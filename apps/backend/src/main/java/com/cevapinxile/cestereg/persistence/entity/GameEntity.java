/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.entity;

import com.cevapinxile.cestereg.api.quiz.dto.request.CreateGameRequest;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 *
 * @author denijal
 */
@Entity
@Table(schema = "public", name = "game")
@NamedQueries({
    @NamedQuery(name = "GameEntity.findAll", query = "SELECT g FROM GameEntity g"),
    @NamedQuery(name = "GameEntity.findByDate", query = "SELECT g FROM GameEntity g WHERE g.date = :date"),
    @NamedQuery(name = "GameEntity.findByStage", query = "SELECT g FROM GameEntity g WHERE g.stage = :stage"),
    @NamedQuery(name = "GameEntity.findByMaxSongs", query = "SELECT g FROM GameEntity g WHERE g.maxSongs = :maxSongs"),
    @NamedQuery(name = "GameEntity.findByMaxAlbums", query = "SELECT g FROM GameEntity g WHERE g.maxAlbums = :maxAlbums"),
    @NamedQuery(name = "GameEntity.findByCode", query = "SELECT g FROM GameEntity g WHERE g.code = :roomCode"),
    @NamedQuery(name = "GameEntity.findByPasswordHash", query = "SELECT g FROM GameEntity g WHERE g.passwordHash = :passwordHash")})
public class GameEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "id")
    private UUID id;
    @Basic(optional = false)
    @NotNull
    @Column(name = "date")
    private LocalDateTime date;
    @Basic(optional = false)
    @Column(name = "stage")
    private int stage = 0;
    @Basic(optional = false)
    @Column(name = "max_songs")
    private int maxSongs = 10;
    @Basic(optional = false)
    @Column(name = "max_albums")
    private int maxAlbums = 10;
    @Size(max = 4)
    @Column(name = "code")
    private String code;
    @Size(max = 128)
    @Column(name = "password_hash")
    private String passwordHash;
    @OneToMany(mappedBy = "gameId")
    private List<TeamEntity> teamList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "gameId")
    private List<CategoryEntity> categoryList;

    public GameEntity() {
    }

    public GameEntity(UUID id) {
        this.id = id;
    }

    public GameEntity(UUID id, LocalDateTime date, int stage, int maxSongs, int maxAlbums) {
        this.id = id;
        this.date = date;
        this.stage = stage;
        this.maxSongs = maxSongs;
        this.maxAlbums = maxAlbums;
    }

    public GameEntity(CreateGameRequest cgr) {
        this.id = UUID.randomUUID();
        this.date = LocalDateTime.now();
        
        if(cgr.maxAlbums()!= null) this.maxAlbums = cgr.maxAlbums();
        if(cgr.maxSongs()!= null) this.maxSongs = cgr.maxSongs();
        
        this.code = new Random().ints(4, (int) 'A', (int)'Z' +1)
                               .mapToObj(i -> "" + (char) i)
                               .collect(Collectors.joining());
    }
    
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public int getMaxSongs() {
        return maxSongs;
    }

    public void setMaxSongs(int maxSongs) {
        this.maxSongs = maxSongs;
    }

    public int getMaxAlbums() {
        return maxAlbums;
    }

    public void setMaxAlbums(int maxAlbums) {
        this.maxAlbums = maxAlbums;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @XmlTransient
    public List<TeamEntity> getTeamList() {
        return teamList;
    }

    public void setTeamList(List<TeamEntity> teamList) {
        this.teamList = teamList;
    }

    @XmlTransient
    public List<CategoryEntity> getCategoryList() {
        return categoryList;
    }

    public void setCategoryList(List<CategoryEntity> categoryList) {
        this.categoryList = categoryList;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof GameEntity)) {
            return false;
        }
        GameEntity other = (GameEntity) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.persistence.entity.Game[ id=" + id + " ]";
    }
    
}
