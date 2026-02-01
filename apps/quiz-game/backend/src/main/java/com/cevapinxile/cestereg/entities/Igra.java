/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.entities;

import com.cevapinxile.cestereg.models.requests.CreateIgraRequest;
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
@Table(schema = "public", name = "igra")
@NamedQueries({
    @NamedQuery(name = "Igra.findAll", query = "SELECT i FROM Igra i"),
    @NamedQuery(name = "Igra.findByDatum", query = "SELECT i FROM Igra i WHERE i.datum = :datum"),
    @NamedQuery(name = "Igra.findByStatus", query = "SELECT i FROM Igra i WHERE i.status = :status"),
    @NamedQuery(name = "Igra.findByBrojPjesama", query = "SELECT i FROM Igra i WHERE i.brojPjesama = :brojPjesama"),
    @NamedQuery(name = "Igra.findByBrojAlbuma", query = "SELECT i FROM Igra i WHERE i.brojAlbuma = :brojAlbuma"),
    @NamedQuery(name = "Igra.findByKod", query = "SELECT i FROM Igra i WHERE i.kod = :kod AND i.status != -1")})
public class Igra implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @Basic(optional = false)
    @Column(name = "datum")
    private LocalDateTime datum;
    @Basic(optional = false)
    @Column(name = "status")
    private int status = 0;
    @Basic(optional = false)
    @Column(name = "broj_pjesama")
    private int brojPjesama = 10;
    @Basic(optional = false)
    @Column(name = "broj_albuma")
    private int brojAlbuma = 10;
    @OneToMany(mappedBy = "igraId")
    private List<Tim> timList;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "igraId")
    private List<Kategorija> kategorijaList;
    @Column(name = "kod")
    private String kod;

    public Igra() {
    }

    public Igra(UUID id) {
        this.id = id;
    }

    public Igra(UUID id, LocalDateTime datum, int status, int brojPjesama, int brojAlbuma) {
        this.id = id;
        this.datum = datum;
        this.status = status;
        this.brojPjesama = brojPjesama;
        this.brojAlbuma = brojAlbuma;
    }

    public Igra(CreateIgraRequest cir) {
        
        this.id = UUID.randomUUID();
        this.datum = LocalDateTime.now();
        
        if(cir.broj_albuma() != null) this.brojAlbuma = cir.broj_albuma();
        if(cir.broj_pjesama()!= null) this.brojPjesama = cir.broj_pjesama();
        
        this.kod = new Random().ints(4, (int) 'A', (int)'Z' +1)
                               .mapToObj(i -> "" + (char) i)
                               .collect(Collectors.joining());
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getDatum() {
        return datum;
    }

    public void setDatum(LocalDateTime datum) {
        this.datum = datum;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getBrojPjesama() {
        return brojPjesama;
    }

    public void setBrojPjesama(int brojPjesama) {
        this.brojPjesama = brojPjesama;
    }

    public int getBrojAlbuma() {
        return brojAlbuma;
    }

    public void setBrojAlbuma(int brojAlbuma) {
        this.brojAlbuma = brojAlbuma;
    }

    public List<Tim> getTimList() {
        return timList;
    }

    public void setTimList(List<Tim> timList) {
        this.timList = timList;
    }

    public List<Kategorija> getKategorijaList() {
        return kategorijaList;
    }

    public void setKategorijaList(List<Kategorija> kategorijaList) {
        this.kategorijaList = kategorijaList;
    }

    public String getKod() {
        return kod;
    }

    public void setKod(String kod) {
        this.kod = kod;
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
        if (!(object instanceof Igra)) {
            return false;
        }
        Igra other = (Igra) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.entities.Igra[ id=" + id + " ]";
    }
    
}
