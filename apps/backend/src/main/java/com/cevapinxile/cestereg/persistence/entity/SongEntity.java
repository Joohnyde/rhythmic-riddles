/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.entity;

import jakarta.persistence.Basic;
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
@Table(name = "song")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "SongEntity.findAll", query = "SELECT s FROM SongEntity s"),
    @NamedQuery(name = "SongEntity.findByAuthors", query = "SELECT s FROM SongEntity s WHERE s.authors = :authors"),
    @NamedQuery(name = "SongEntity.findByName", query = "SELECT s FROM SongEntity s WHERE s.name = :name"),
    @NamedQuery(name = "SongEntity.findBySnippetDuration", query = "SELECT s FROM SongEntity s WHERE s.snippetDuration = :snippetDuration"),
    @NamedQuery(name = "SongEntity.findByAnswerDuration", query = "SELECT s FROM SongEntity s WHERE s.answerDuration = :answerDuration")})
public class SongEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "id")
    private UUID id;
    @Size(max = 2147483647)
    @Column(name = "authors")
    private String authors;
    @Size(max = 2147483647)
    @Column(name = "name")
    private String name;
    @Column(name = "snippet_duration")
    private Double snippetDuration;
    @Column(name = "answer_duration")
    private Double answerDuration;
    @OneToMany(mappedBy = "songId")
    private List<TrackEntity> trackList;

    public SongEntity() {
    }

    public SongEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getSnippetDuration() {
        return snippetDuration;
    }

    public void setSnippetDuration(Double snippetDuration) {
        this.snippetDuration = snippetDuration;
    }

    public Double getAnswerDuration() {
        return answerDuration;
    }

    public void setAnswerDuration(Double answerDuration) {
        this.answerDuration = answerDuration;
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
        if (!(object instanceof SongEntity)) {
            return false;
        }
        SongEntity other = (SongEntity) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return authors+" - "+name;
    }
    
}
