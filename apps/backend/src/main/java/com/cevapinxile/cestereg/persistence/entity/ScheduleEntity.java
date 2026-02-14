/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.entity;

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
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(name = "schedule")
@NamedQueries({
    @NamedQuery(name = "ScheduleEntity.findAll", query = "SELECT s FROM ScheduleEntity s"),
    @NamedQuery(name = "ScheduleEntity.findByOrdinalNumber", query = "SELECT s FROM ScheduleEntity s WHERE s.ordinalNumber = :ordinalNumber"),
    @NamedQuery(name = "ScheduleEntity.findByStartedAt", query = "SELECT s FROM ScheduleEntity s WHERE s.startedAt = :startedAt"),
    @NamedQuery(name = "ScheduleEntity.findByRevealedAt", query = "SELECT s FROM ScheduleEntity s WHERE s.revealedAt = :revealedAt"),
    @NamedQuery(name = "ScheduleEntity.findLastPlayed", query = """
                                                        SELECT s
                                                        FROM   CategoryEntity c
                                                               LEFT JOIN c.scheduleList s
                                                                      ON s.startedAt IS NOT NULL
                                                        WHERE  c.gameId.id = :gameId
                                                               AND c.ordinalNumber IS NOT NULL
                                                        ORDER  BY c.ordinalNumber DESC,
                                                                  s.ordinalNumber DESC
                                                        LIMIT  1 """),
    @NamedQuery(name = "ScheduleEntity.findNext", query = """
                                                        SELECT s
                                                        FROM   ScheduleEntity s
                                                        WHERE  s.categoryId.gameId.id = :gameId
                                                               AND s.startedAt IS NULL
                                                        ORDER  BY s.ordinalNumber ASC
                                                        LIMIT  1 """)})
public class ScheduleEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "ordinal_number")
    private Integer ordinalNumber;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "revealed_at")
    private LocalDateTime revealedAt;
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    @ManyToOne
    private CategoryEntity categoryId;
    @JoinColumn(name = "track_id", referencedColumnName = "id")
    @ManyToOne
    private TrackEntity trackId;
    @OneToMany(mappedBy = "scheduleId")
    private List<InterruptEntity> interruptList;

    public ScheduleEntity() {
    }

    public ScheduleEntity(UUID id) {
        this.id = id;
    }

    public ScheduleEntity(CategoryEntity category, TrackEntity track, int ordinalNumber) {
        this.id = UUID.randomUUID();
        this.categoryId = category;
        this.trackId = track;
        this.ordinalNumber = ordinalNumber;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getOrdinalNumber() {
        return ordinalNumber;
    }

    public void setOrdinalNumber(Integer ordinalNumber) {
        this.ordinalNumber = ordinalNumber;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getRevealedAt() {
        return revealedAt;
    }

    public void setRevealedAt(LocalDateTime revealedAt) {
        this.revealedAt = revealedAt;
    }

    public CategoryEntity getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(CategoryEntity categoryId) {
        this.categoryId = categoryId;
    }

    public TrackEntity getTrackId() {
        return trackId;
    }

    public void setTrackId(TrackEntity trackId) {
        this.trackId = trackId;
    }

    @XmlTransient
    public List<InterruptEntity> getInterruptList() {
        return interruptList;
    }

    public void setInterruptList(List<InterruptEntity> interruptList) {
        this.interruptList = interruptList;
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
        if (!(object instanceof ScheduleEntity)) {
            return false;
        }
        ScheduleEntity other = (ScheduleEntity) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.persistence.entity.Schedule[ id=" + id + " ]";
    }
    
}
