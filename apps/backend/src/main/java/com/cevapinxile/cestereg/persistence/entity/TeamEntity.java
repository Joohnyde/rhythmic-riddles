/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.entity;

import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
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
import jakarta.validation.constraints.Size;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(name = "team")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "TeamEntity.findAll", query = "SELECT t FROM TeamEntity t"),
    @NamedQuery(name = "TeamEntity.findByButtonCode", query = "SELECT t FROM TeamEntity t WHERE t.buttonCode = :buttonCode"),
    @NamedQuery(name = "TeamEntity.findByName", query = "SELECT t FROM TeamEntity t WHERE t.name = :name"),
    @NamedQuery(name = "TeamEntity.findByImage", query = "SELECT t FROM TeamEntity t WHERE t.image = :image"),
    @NamedQuery(name = "TeamEntity.findByGameId", query = "SELECT new com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse(t.id, t.name, t.image) FROM TeamEntity t WHERE t.gameId.code = :roomCode")})
public class TeamEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "id")
    private UUID id;
    @Size(max = 2147483647)
    @Column(name = "button_code")
    private String buttonCode;
    @Size(max = 2147483647)
    @Column(name = "name")
    private String name;
    @Size(max = 2147483647)
    @Column(name = "image")
    private String image;
    @OneToMany(mappedBy = "teamId")
    private List<InterruptEntity> interruptList;
    @JoinColumn(name = "game_id", referencedColumnName = "id")
    @ManyToOne
    private GameEntity gameId;
    @OneToMany(mappedBy = "pickedByTeamId")
    private List<CategoryEntity> categoryList;

    public TeamEntity() {
    }

    public TeamEntity(UUID id) {
        this.id = id;
    }

    public TeamEntity(CreateTeamRequest ctr, UUID gameId) {
        this.id = UUID.randomUUID();
        this.gameId = new GameEntity(gameId);
        this.image = ctr.image();
        this.name = ctr.name();
        this.buttonCode = ctr.buttonCode();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getButtonCode() {
        return buttonCode;
    }

    public void setButtonCode(String buttonCode) {
        this.buttonCode = buttonCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    @XmlTransient
    public List<InterruptEntity> getInterruptList() {
        return interruptList;
    }

    public void setInterruptList(List<InterruptEntity> interruptList) {
        this.interruptList = interruptList;
    }

    public GameEntity getGameId() {
        return gameId;
    }

    public void setGameId(GameEntity gameId) {
        this.gameId = gameId;
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
        if (!(object instanceof TeamEntity)) {
            return false;
        }
        TeamEntity other = (TeamEntity) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.persistence.entity.Team[ id=" + id + " ]";
    }

}
