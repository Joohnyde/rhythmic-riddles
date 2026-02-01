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
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(schema = "public", name = "odgovor")
@NamedQueries({
    @NamedQuery(name = "Odgovor.findAll", query = "SELECT o FROM Odgovor o"),
    @NamedQuery(name = "Odgovor.findByVremeStigao", query = "SELECT o FROM Odgovor o WHERE o.vremeStigao = :vremeStigao"),
    @NamedQuery(name = "Odgovor.findByVremeResen", query = "SELECT o FROM Odgovor o WHERE o.vremeResen = :vremeResen"),
    @NamedQuery(name = "Odgovor.findByTacan", query = "SELECT o FROM Odgovor o WHERE o.tacan = :tacan"),
    @NamedQuery(name = "Odgovor.findByBodovi", query = "SELECT o FROM Odgovor o WHERE o.bodovi = :bodovi"),
    @NamedQuery(name = "Odgovor.findCorrectAnswer", query = "SELECT o.timId.id FROM Odgovor o WHERE o.redoslijedId.id = :id AND o.tacan = true"),
    @NamedQuery(name = "Odgovor.findInterrupts", query = """
                                                         SELECT NEW com.cevapinxile.cestereg.models.responses.InterruptFrame(
                                                             o1.vremeStigao,
                                                             o1.vremeResen
                                                         )
                                                         FROM Odgovor o1
                                                         WHERE o1.vremeStigao > :start_timestamp
                                                           AND o1.redoslijedId.id = :red_id
                                                           AND NOT EXISTS (
                                                               SELECT o2
                                                               FROM Odgovor o2
                                                               WHERE o2.vremeStigao > :start_timestamp
                                                                 AND o2.redoslijedId.id = :red_id
                                                                 AND o2.id <> o1.id
                                                                 AND (
                                                                       o2.vremeResen >= o1.vremeResen
                                                                    OR o2.vremeResen IS NULL
                                                                 )
                                                                 AND o2.vremeStigao <= o1.vremeStigao
                                                           )
                                                         ORDER BY o1.vremeStigao ASC
                                                         """),
    @NamedQuery(name = "Odgovor.findLastPause", query = "SELECT o1 FROM Odgovor o1 WHERE o1.vremeStigao > :start_timestamp AND o1.redoslijedId.id = :red_id ORDER BY o1.vremeStigao DESC LIMIT 1"),
    @NamedQuery(name = "Odgovor.findLastAnswer", query = "SELECT o1 FROM Odgovor o1 WHERE o1.timId IS NOT NULL AND o1.vremeStigao > :start_timestamp AND o1.redoslijedId.id = :red_id ORDER BY o1.vremeStigao DESC LIMIT 1"),
    @NamedQuery(name = "Odgovor.isTimOdgovarao", query = """
                                                        SELECT TRUE
                                                        FROM Odgovor o
                                                        LEFT JOIN o.redoslijedId r
                                                        LEFT JOIN r.kategorijaId k
                                                        WHERE o.timId.id = :timId
                                                          AND k.rbr IS NOT NULL
                                                          AND r.vremePocetka IS NOT NULL
                                                        ORDER BY k.rbr DESC, r.rbr DESC
                                                        LIMIT 1
                                                        """),
    @NamedQuery(name = "Odgovor.resolveError", query = """
                                                        UPDATE Odgovor o
                                                               SET o.vremeResen = :end
                                                               WHERE o.timId IS NULL
                                                                 AND o.redoslijedId.id = :redoslijedId
                                                                 AND o.vremeResen IS NULL
                                                        """),
    @NamedQuery(name = "Odgovor.getPreviousScenario", query = """
                                                        SELECT o.bodovi
                                                        FROM Odgovor o
                                                               WHERE o.timId IS NULL
                                                                 AND o.redoslijedId.id = :redoslijedId
                                                                 AND o.vremeResen IS NULL
                                                                 AND o.bodovi IS NOT NULL
                                                                 AND o.bodovi != 3
                                                       LIMIT 1
                                                        """)})
public class Odgovor implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "vreme_stigao")
    private LocalDateTime vremeStigao;
    @Column(name = "vreme_resen")
    private LocalDateTime vremeResen;
    @Column(name = "tacan")
    private Boolean tacan;
    @Column(name = "bodovi")
    private Integer bodovi;
    @JoinColumn(name = "redoslijed_id", referencedColumnName = "id")
    @ManyToOne
    private Redoslijed redoslijedId;
    @JoinColumn(name = "tim_id", referencedColumnName = "id")
    @ManyToOne
    private Tim timId;

    public Odgovor() {
    }

    public Odgovor(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getVremeStigao() {
        return vremeStigao;
    }

    public void setVremeStigao(LocalDateTime vremeStigao) {
        this.vremeStigao = vremeStigao;
    }

    public LocalDateTime getVremeResen() {
        return vremeResen;
    }

    public void setVremeResen(LocalDateTime vremeResen) {
        this.vremeResen = vremeResen;
    }

    public Boolean getTacan() {
        return tacan;
    }

    public void setTacan(Boolean tacan) {
        this.tacan = tacan;
    }

    public Integer getBodovi() {
        return bodovi;
    }

    public void setBodovi(Integer bodovi) {
        this.bodovi = bodovi;
    }

    public Redoslijed getRedoslijedId() {
        return redoslijedId;
    }

    public void setRedoslijedId(Redoslijed redoslijedId) {
        this.redoslijedId = redoslijedId;
    }

    public Tim getTimId() {
        return timId;
    }

    public void setTimId(Tim timId) {
        this.timId = timId;
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
        if (!(object instanceof Odgovor)) {
            return false;
        }
        Odgovor other = (Odgovor) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.entities.Odgovor[ id=" + id + " ]";
    }
    
}
