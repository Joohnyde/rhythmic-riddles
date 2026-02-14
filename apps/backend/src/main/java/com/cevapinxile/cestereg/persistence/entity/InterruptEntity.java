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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Entity
@Table(schema = "public", name = "interrupt")
@NamedQueries({
    @NamedQuery(name = "InterruptEntity.findAll", query = "SELECT i FROM InterruptEntity i"),
    @NamedQuery(name = "InterruptEntity.findByArrivedAt", query = "SELECT i FROM InterruptEntity i WHERE i.arrivedAt = :arrivedAt"),
    @NamedQuery(name = "InterruptEntity.findByResolvedAt", query = "SELECT i FROM InterruptEntity i WHERE i.resolvedAt = :resolvedAt"),
    @NamedQuery(name = "InterruptEntity.findByIsCorrect", query = "SELECT i FROM InterruptEntity i WHERE i.isCorrect = :isCorrect"),
    @NamedQuery(name = "InterruptEntity.findByScoreOrScenarioId", query = "SELECT i FROM InterruptEntity i WHERE i.scoreOrScenarioId = :scoreOrScenarioId"),
    @NamedQuery(name = "InterruptEntity.findInterrupts", query = """
                                                          SELECT   NEW com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame( i1.arrivedAt, i1.resolvedAt )
                                                          FROM     InterruptEntity i1
                                                          WHERE    i1.arrivedAt > :startTimestamp
                                                          AND      i1.scheduleId.id = :scheduleId
                                                          AND      NOT EXISTS
                                                                   (
                                                                          SELECT i2
                                                                          FROM   InterruptEntity i2
                                                                          WHERE  i2.arrivedAt > :startTimestamp
                                                                          AND    i2.scheduleId.id = :scheduleId
                                                                          AND    i2.id <> i1.id
                                                                          AND    (
                                                                                        i2.resolvedAt >= i1.resolvedAt
                                                                                 OR     i2.resolvedAt IS NULL )
                                                                          AND    i2.arrivedAt <= i1.arrivedAt )
                                                          ORDER BY i1.arrivedAt ASC
                                                         """),
    @NamedQuery(name = "InterruptEntity.findLastPause", query = "SELECT i FROM InterruptEntity i WHERE i.arrivedAt > :startTimestamp AND i.scheduleId.id = :scheduleId ORDER BY i.arrivedAt DESC LIMIT 1"),
    @NamedQuery(name = "InterruptEntity.findLastAnswer", query = "SELECT i FROM InterruptEntity i WHERE i.teamId IS NOT NULL AND i.arrivedAt > :startTimestamp AND i.scheduleId.id = :scheduleId ORDER BY i.arrivedAt DESC LIMIT 1"),
    @NamedQuery(name = "InterruptEntity.didTeamAnswer", query = """
                                                        SELECT    TRUE
                                                        FROM      InterruptEntity i
                                                        LEFT JOIN i.scheduleId s
                                                        LEFT JOIN s.categoryId c
                                                        WHERE     i.teamId.id = :teamId
                                                        AND       c.ordinalNumber IS NOT NULL
                                                        AND       s.startedAt IS NOT NULL
                                                        ORDER BY  c.ordinalNumber DESC,
                                                                  s.ordinalNumber DESC LIMIT 1
                                                        """),
    @NamedQuery(name = "InterruptEntity.resolveErrors", query = """
                                                        UPDATE InterruptEntity i
                                                        SET    i.resolvedAt = :resolvedAt
                                                        WHERE  i.teamId IS NULL
                                                               AND i.scheduleId.id = :scheduleId
                                                               AND i.resolvedAt IS NULL 
                                                        """),
    @NamedQuery(name = "InterruptEntity.findCorrectAnswer", query = "SELECT i.teamId.id FROM InterruptEntity i WHERE i.scheduleId.id = :scheduleId AND i.isCorrect = true"),
    @NamedQuery(name = "InterruptEntity.findPreviousScenarioId", query = """
                                                        SELECT i.scoreOrScenarioId
                                                        FROM   InterruptEntity i
                                                        WHERE  i.teamId IS NULL
                                                               AND i.scheduleId.id = :scheduleId
                                                               AND i.resolvedAt IS NULL
                                                               AND i.scoreOrScenarioId IS NOT NULL
                                                               AND i.scoreOrScenarioId != 3
                                                        LIMIT  1 
                                                        """)})
public class InterruptEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Basic(optional = false)
    @NotNull
    @Lob
    @Column(name = "id")
    private UUID id;
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    @Column(name = "is_correct")
    private Boolean isCorrect;
    @Column(name = "score_or_scenario_id")
    private Integer scoreOrScenarioId;
    @JoinColumn(name = "schedule_id", referencedColumnName = "id")
    @ManyToOne
    private ScheduleEntity scheduleId;
    @JoinColumn(name = "team_id", referencedColumnName = "id")
    @ManyToOne
    private TeamEntity teamId;

    public InterruptEntity() {
    }

    public InterruptEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(LocalDateTime arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(Boolean correct) {
        this.isCorrect = correct;
    }

    public Integer getScoreOrScenarioId() {
        return scoreOrScenarioId;
    }

    public void setScoreOrScenarioId(Integer scoreOrScenarioId) {
        this.scoreOrScenarioId = scoreOrScenarioId;
    }

    public ScheduleEntity getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(ScheduleEntity scheduleId) {
        this.scheduleId = scheduleId;
    }

    public TeamEntity getTeamId() {
        return teamId;
    }

    public void setTeamId(TeamEntity teamId) {
        this.teamId = teamId;
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
        if (!(object instanceof InterruptEntity)) {
            return false;
        }
        InterruptEntity other = (InterruptEntity) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "com.cevapinxile.cestereg.persistence.entity.Interrupt[ id=" + id + " ]";
    }

}
