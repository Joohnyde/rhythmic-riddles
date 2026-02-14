/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(schema = "public", name = "category")
@NamedQueries({
    @NamedQuery(name = "CategoryEntity.findAll", query = "SELECT c FROM CategoryEntity c"),
    @NamedQuery(name = "CategoryEntity.findByOrdinalNumber", query = "SELECT c FROM CategoryEntity c WHERE c.ordinalNumber = :ordinalNumber"),
    @NamedQuery(name = "CategoryEntity.findByIsDone", query = "SELECT c FROM CategoryEntity c WHERE c.isDone = :isDone"),
    @NamedQuery(name = "CategoryEntity.findByGameId", query = "SELECT new com.cevapinxile.cestereg.api.quiz.dto.response.CategorySimple(c) FROM CategoryEntity c WHERE c.gameId.id = :gameId"),
    @NamedQuery(name = "CategoryEntity.findNextId", query = "SELECT COALESCE(MAX(c.ordinalNumber), 0)+1 FROM CategoryEntity c WHERE c.gameId.id = :gameId"),
    @NamedQuery(name = "CategoryEntity.findLastCategory", query = "SELECT new com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory(c) FROM CategoryEntity c WHERE c.gameId.id = :gameId AND c.ordinalNumber IS NOT NULL ORDER BY c.ordinalNumber DESC LIMIT 1")})
public class CategoryEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "ordinal_number")
    private Integer ordinalNumber;
    @Column(name = "is_done")
    private Boolean isDone;
    @OneToMany(mappedBy = "categoryId")
    private List<ScheduleEntity> scheduleList;
    @JoinColumn(name = "album_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private AlbumEntity albumId;
    @JoinColumn(name = "game_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private GameEntity gameId;
    @JoinColumn(name = "picked_by_team_id", referencedColumnName = "id")
    @ManyToOne
    private TeamEntity pickedByTeamId;

    public CategoryEntity() {
    }

    public CategoryEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getOrdinalNumber() {
        return ordinalNumber;
    }

    public void setOrdinalNumber(Integer ordinalNumber) {
        this.ordinalNumber = ordinalNumber;
    }

    public Boolean isDone() {
        return isDone;
    }

    public void setDone(Boolean done) {
        this.isDone = done;
    }

    @XmlTransient
    public List<ScheduleEntity> getScheduleList() {
        return scheduleList;
    }

    public void setScheduleList(List<ScheduleEntity> scheduleList) {
        this.scheduleList = scheduleList;
    }

    public AlbumEntity getAlbumId() {
        return albumId;
    }

    public void setAlbumId(AlbumEntity albumId) {
        this.albumId = albumId;
    }

    public GameEntity getGameId() {
        return gameId;
    }

    public void setGameId(GameEntity gameId) {
        this.gameId = gameId;
    }

    public TeamEntity getPickedByTeamId() {
        return pickedByTeamId;
    }

    public void setPickedByTeamId(TeamEntity pickedByTeamId) {
        this.pickedByTeamId = pickedByTeamId;
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
        if (!(object instanceof CategoryEntity)) {
            return false;
        }
        CategoryEntity other = (CategoryEntity) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.persistence.entity.Category[ id=" + id + " ]";
    }
    
}
