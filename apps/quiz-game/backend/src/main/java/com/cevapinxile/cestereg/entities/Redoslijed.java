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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(schema = "public", name = "redoslijed")
@NamedQueries({
    @NamedQuery(name = "Redoslijed.findAll", query = "SELECT r FROM Redoslijed r"),
    @NamedQuery(name = "Redoslijed.findByRbr", query = "SELECT r FROM Redoslijed r WHERE r.rbr = :rbr"),
    @NamedQuery(name = "Redoslijed.findByVremePocetka", query = "SELECT r FROM Redoslijed r WHERE r.vremePocetka = :vremePocetka"),
    @NamedQuery(name = "Redoslijed.findByVremeKraja", query = "SELECT r FROM Redoslijed r WHERE r.vremeKraja = :vremeKraja"),
    @NamedQuery(name = "Redoslijed.findZadnja", query = """
                                                        SELECT r
                                                        FROM Kategorija k
                                                        LEFT JOIN k.redoslijedList r 
                                                               ON r.vremePocetka IS NOT NULL
                                                        WHERE k.igraId.id = :igraId
                                                          AND k.rbr IS NOT NULL
                                                        ORDER BY k.rbr DESC, r.rbr DESC
                                                        LIMIT 1"""),
    @NamedQuery(name = "Redoslijed.getNext", query = """
                                                        SELECT r 
                                                        FROM Redoslijed r
                                                        WHERE r.kategorijaId.igraId.id = :igraId
                                                          AND r.vremePocetka IS NULL
                                                        ORDER BY r.rbr ASC
                                                        LIMIT 1""")})

public class Redoslijed implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "rbr")
    private Integer rbr;
    @Column(name = "vreme_pocetka")
    private LocalDateTime vremePocetka;
    @Column(name = "vreme_kraja")
    private LocalDateTime vremeKraja;
    @OneToMany(mappedBy = "redoslijedId")
    private List<Odgovor> odgovorList;
    @JoinColumn(name = "numera_id", referencedColumnName = "id")
    @ManyToOne
    private Numera numeraId;
    @JoinColumn(name = "kategorija_id", referencedColumnName = "id")
    @ManyToOne
    private Kategorija kategorijaId;

    public Redoslijed() {
    }

    public Redoslijed(UUID id) {
        this.id = id;
    }

    public Redoslijed(Kategorija kategorija, Numera numera, int rbr) {
        this.id = UUID.randomUUID();
        this.kategorijaId = kategorija;
        this.numeraId = numera;
        this.rbr = rbr;
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

    public LocalDateTime getVremePocetka() {
        return vremePocetka;
    }

    public void setVremePocetka(LocalDateTime vremePocetka) {
        this.vremePocetka = vremePocetka;
    }

    public LocalDateTime getVremeKraja() {
        return vremeKraja;
    }

    public void setVremeKraja(LocalDateTime vremeKraja) {
        this.vremeKraja = vremeKraja;
    }

    public List<Odgovor> getOdgovorList() {
        return odgovorList;
    }

    public void setOdgovorList(List<Odgovor> odgovorList) {
        this.odgovorList = odgovorList;
    }

    public Numera getNumeraId() {
        return numeraId;
    }

    public void setNumeraId(Numera numeraId) {
        this.numeraId = numeraId;
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
        if (!(object instanceof Redoslijed)) {
            return false;
        }
        Redoslijed other = (Redoslijed) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.entities.Redoslijed[ id=" + id + " ]";
    }
    
}
