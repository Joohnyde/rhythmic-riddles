/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.entity;

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
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(schema = "public", name = "album")
@NamedQueries({
    @NamedQuery(name = "AlbumEntity.findAll", query = "SELECT a FROM AlbumEntity a"),
    @NamedQuery(name = "AlbumEntity.findByName", query = "SELECT a FROM AlbumEntity a WHERE a.name = :name"),
    @NamedQuery(name = "AlbumEntity.findByCustomQuestion", query = "SELECT a FROM AlbumEntity a WHERE a.customQuestion = :customQuestion")})
public class AlbumEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "id")
    private UUID id;
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "name")
    private String name;
    @Size(max = 2147483647)
    @Column(name = "custom_question")
    private String customQuestion;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "albumId")
    private List<CategoryEntity> categoryList;
    @OneToMany(mappedBy = "albumId")
    private List<TrackEntity> trackList;

    public AlbumEntity() {
    }

    public AlbumEntity(UUID id) {
        this.id = id;
    }

    public AlbumEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomQuestion() {
        return customQuestion;
    }

    public void setCustomQuestion(String customQuestion) {
        this.customQuestion = customQuestion;
    }

    @XmlTransient
    public List<CategoryEntity> getCategoryList() {
        return categoryList;
    }

    public void setCategoryList(List<CategoryEntity> categoryList) {
        this.categoryList = categoryList;
    }

    @XmlTransient
    public List<TrackEntity> getTrackList() {
        return trackList;
    }

    public void setTrackList(List<TrackEntity> trackList) {
        this.trackList = trackList;
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
        if (!(object instanceof AlbumEntity)) {
            return false;
        }
        AlbumEntity other = (AlbumEntity) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.persistence.entity.Album[ id=" + id + " ]";
    }
    
}
