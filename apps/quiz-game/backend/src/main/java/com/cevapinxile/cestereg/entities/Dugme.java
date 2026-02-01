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
@Table(schema = "public", name = "dugme")
@NamedQueries({
    @NamedQuery(name = "Dugme.findAll", query = "SELECT d FROM Dugme d")})
public class Dugme implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @OneToMany(mappedBy = "dugmeId")
    private List<Tim> timList;

    public Dugme() {
    }

    public Dugme(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public List<Tim> getTimList() {
        return timList;
    }

    public void setTimList(List<Tim> timList) {
        this.timList = timList;
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
        if (!(object instanceof Dugme)) {
            return false;
        }
        Dugme other = (Dugme) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.entities.Dugme[ id=" + id + " ]";
    }
    
}
