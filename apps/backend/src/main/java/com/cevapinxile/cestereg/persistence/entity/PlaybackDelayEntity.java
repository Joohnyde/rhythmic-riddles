package com.cevapinxile.cestereg.persistence.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * @author denijal
 */
@Entity
@Table(name = "playback_delay")
@NamedQueries({
  @NamedQuery(name = "PlaybackDelayEntity.findAll", query = "SELECT p FROM PlaybackDelayEntity p"),
  @NamedQuery(
      name = "PlaybackDelayEntity.findBySchedule",
      query =
          """
          SELECT p
          FROM PlaybackDelayEntity p
          WHERE p.scheduleId.id = :scheduleId
          ORDER BY p.sentAt ASC
          """),
  @NamedQuery(
      name = "PlaybackDelayEntity.findOpenBySchedule",
      query =
          """
          SELECT p
          FROM PlaybackDelayEntity p
          WHERE p.scheduleId.id = :scheduleId
            AND p.acknowledgedAt IS NULL
          """)
})
public class PlaybackDelayEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Basic(optional = false)
  @NotNull
  @Column(name = "id")
  private UUID id;

  @JoinColumn(name = "schedule_id", referencedColumnName = "id", nullable = false)
  @ManyToOne(optional = false)
  @NotNull
  private ScheduleEntity scheduleId;

  @Basic(optional = false)
  @NotNull
  @Column(name = "sent_at", nullable = false)
  private LocalDateTime sentAt;

  @Column(name = "acknowledged_at")
  private LocalDateTime acknowledgedAt;

  public PlaybackDelayEntity() {}

  public PlaybackDelayEntity(UUID id) {
    this.id = id;
  }

  public PlaybackDelayEntity(ScheduleEntity schedule, LocalDateTime sentAt) {
    this.id = UUID.randomUUID();
    this.scheduleId = schedule;
    this.sentAt = sentAt;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public ScheduleEntity getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(ScheduleEntity scheduleId) {
    this.scheduleId = scheduleId;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(LocalDateTime sentAt) {
    this.sentAt = sentAt;
  }

  public LocalDateTime getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
    this.acknowledgedAt = acknowledgedAt;
  }

  @Override
  public String toString() {
    return "com.cevapinxile.cestereg.persistence.entity.PlaybackDelay[ id=" + id + " ]";
  }
}
