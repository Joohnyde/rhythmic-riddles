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
@Table(schema = "public", name = "kategorija")
@NamedQueries({
    @NamedQuery(name = "Kategorija.findAll", query = "SELECT k FROM Kategorija k"),
    @NamedQuery(name = "Kategorija.findByRbr", query = "SELECT k FROM Kategorija k WHERE k.rbr = :rbr"),
    @NamedQuery(name = "Kategorija.findByGotovo", query = "SELECT k FROM Kategorija k WHERE k.gotovo = :gotovo"),
    @NamedQuery(name = "Kategorija.findByIgra", query = "SELECT new com.cevapinxile.cestereg.models.responses.KategorijaSimple(k) FROM Kategorija k WHERE k.igraId.id = :igra"),
    @NamedQuery(name = "Kategorija.getNextID", query = "SELECT COALESCE(MAX(k.rbr), 0)+1 FROM Kategorija k WHERE k.igraId.id = :igra"),
    @NamedQuery(name = "Kategorija.findLast", query = "SELECT new com.cevapinxile.cestereg.models.responses.LastKategorija(k) FROM Kategorija k WHERE k.igraId.id = :igra AND k.rbr IS NOT NULL ORDER BY k.rbr DESC LIMIT 1")})
public class Kategorija implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "rbr")
    private Integer rbr;
    @Column(name = "gotovo")
    private Boolean gotovo;
    @JoinColumn(name = "album_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Album albumId;
    @JoinColumn(name = "igra_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    private Igra igraId;
    @JoinColumn(name = "tim_birao", referencedColumnName = "id")
    @ManyToOne
    private Tim timBirao;
    @OneToMany(mappedBy = "kategorijaId")
    private List<Redoslijed> redoslijedList;

    public Kategorija() {
    }

    public Kategorija(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getRbr() {
        return rbr;
    }

    public void setRbr(Integer rbr) {
        this.rbr = rbr;
    }

    public Boolean getGotovo() {
        return gotovo;
    }

    public void setGotovo(Boolean gotovo) {
        this.gotovo = gotovo;
    }

    public Album getAlbumId() {
        return albumId;
    }

    public void setAlbumId(Album albumId) {
        this.albumId = albumId;
    }

    public Igra getIgraId() {
        return igraId;
    }

    public void setIgraId(Igra igraId) {
        this.igraId = igraId;
    }

    public Tim getTimBirao() {
        return timBirao;
    }

    public void setTimBirao(Tim timBirao) {
        this.timBirao = timBirao;
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
        if (!(object instanceof Kategorija)) {
            return false;
        }
        Kategorija other = (Kategorija) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.entities.Kategorija[ id=" + id + " ]";
    }
    
}
