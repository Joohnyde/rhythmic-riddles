/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.entities;

import com.cevapinxile.cestereg.models.requests.CreateTimRequest;
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
@Table(schema = "public", name = "tim")
@NamedQueries({
    @NamedQuery(name = "Tim.findAll", query = "SELECT t FROM Tim t"),
    @NamedQuery(name = "Tim.findByNaziv", query = "SELECT t FROM Tim t WHERE t.naziv = :naziv"),
    @NamedQuery(name = "Tim.findBySlika", query = "SELECT t FROM Tim t WHERE t.slika = :slika"),
    @NamedQuery(name = "Tim.findByIgra", query = "SELECT new com.cevapinxile.cestereg.models.responses.CreateTimResponse(t.id, t.naziv, t.slika) FROM Tim t WHERE t.igraId.kod = :kod")})
public class Tim implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "naziv")
    private String naziv;
    @Column(name = "slika")
    private String slika;
    @JoinColumn(name = "dugme_id", referencedColumnName = "id")
    @ManyToOne
    private Dugme dugmeId;
    @JoinColumn(name = "igra_id", referencedColumnName = "id")
    @ManyToOne
    private Igra igraId;
    @OneToMany(mappedBy = "timBirao")
    private List<Kategorija> kategorijaList;
    @OneToMany(mappedBy = "timId")
    private List<Odgovor> odgovorList;

    public Tim() {
    }

    public Tim(UUID id) {
        this.id = id;
    }

    public Tim(CreateTimRequest ctr, UUID igraId) {
        this.id = UUID.randomUUID();
        this.dugmeId = new Dugme(ctr.dugme());
        this.igraId = new Igra(igraId);
        this.slika = ctr.slika();
        this.naziv = ctr.ime();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNaziv() {
        return naziv;
    }

    public void setNaziv(String naziv) {
        this.naziv = naziv;
    }

    public String getSlika() {
        return slika;
    }

    public void setSlika(String slika) {
        this.slika = slika;
    }

    public Dugme getDugmeId() {
        return dugmeId;
    }

    public void setDugmeId(Dugme dugmeId) {
        this.dugmeId = dugmeId;
    }

    public Igra getIgraId() {
        return igraId;
    }

    public void setIgraId(Igra igraId) {
        this.igraId = igraId;
    }

    public List<Kategorija> getKategorijaList() {
        return kategorijaList;
    }

    public void setKategorijaList(List<Kategorija> kategorijaList) {
        this.kategorijaList = kategorijaList;
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
        if (!(object instanceof Tim)) {
            return false;
        }
        Tim other = (Tim) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.entities.Tim[ id=" + id + " ]";
    }
    
}
