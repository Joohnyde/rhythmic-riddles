/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.entities;

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
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(schema = "public", name = "numera")
@NamedQueries({
    @NamedQuery(name = "Numera.findAll", query = "SELECT n FROM Numera n"),
    @NamedQuery(name = "Numera.findByOdgovor", query = "SELECT n FROM Numera n WHERE n.odgovor = :odgovor")})
public class Numera implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "odgovor")
    private String odgovor;
    @JoinColumn(name = "album_id", referencedColumnName = "id")
    @ManyToOne
    private Album albumId;
    @JoinColumn(name = "pjesma_id", referencedColumnName = "id")
    @ManyToOne
    private Pjesma pjesmaId;
    @OneToMany(mappedBy = "numeraId")
    private List<Redoslijed> redoslijedList;

    public Numera() {
    }

    public Numera(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOdgovor() {
        return odgovor;
    }

    public void setOdgovor(String odgovor) {
        this.odgovor = odgovor;
    }

    public Album getAlbumId() {
        return albumId;
    }

    public void setAlbumId(Album albumId) {
        this.albumId = albumId;
    }

    public Pjesma getPjesmaId() {
        return pjesmaId;
    }

    public void setPjesmaId(Pjesma pjesmaId) {
        this.pjesmaId = pjesmaId;
    }

    public List<Redoslijed> getRedoslijedList() {
        return redoslijedList;
    }

    public void setRedoslijedList(List<Redoslijed> redoslijedList) {
        this.redoslijedList = redoslijedList;
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
        if (!(object instanceof Numera)) {
            return false;
        }
        Numera other = (Numera) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.entities.Numera[ id=" + id + " ]";
    }
    
}
