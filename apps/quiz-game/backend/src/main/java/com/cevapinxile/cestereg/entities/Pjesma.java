/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.entities;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
@Table(schema = "public", name = "pjesma")
@NamedQueries({
    @NamedQuery(name = "Pjesma.findAll", query = "SELECT p FROM Pjesma p"),
    @NamedQuery(name = "Pjesma.findByAutori", query = "SELECT p FROM Pjesma p WHERE p.autori = :autori"),
    @NamedQuery(name = "Pjesma.findByNaziv", query = "SELECT p FROM Pjesma p WHERE p.naziv = :naziv")})
public class Pjesma implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "autori")
    private String autori;
    @Column(name = "naziv")
    private String naziv;
    @Column(name = "trajanje")
    private Double trajanje;
    @Column(name = "odgovor")
    private Double odgovor;
    @OneToMany(mappedBy = "pjesmaId")
    private List<Numera> numeraList;

    public Pjesma() {
    }

    public Pjesma(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAutori() {
        return autori;
    }

    public void setAutori(String autori) {
        this.autori = autori;
    }

    public String getNaziv() {
        return naziv;
    }

    public void setNaziv(String naziv) {
        this.naziv = naziv;
    }

    public Double getTrajanje() {
        return trajanje;
    }

    public void setTrajanje(Double trajanje) {
        this.trajanje = trajanje;
    }

    public Double getOdgovor() {
        return odgovor;
    }

    public void setOdgovor(Double odgovor) {
        this.odgovor = odgovor;
    }
    
    public List<Numera> getNumeraList() {
        return numeraList;
    }

    public void setNumeraList(List<Numera> numeraList) {
        this.numeraList = numeraList;
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
        if (!(object instanceof Pjesma)) {
            return false;
        }
        Pjesma other = (Pjesma) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return autori+" - "+naziv;
    }
    
}
