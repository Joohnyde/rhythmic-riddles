/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.entity;

import com.cevapinxile.cestereg.e2e.dto.E2eGameFixtureRequest;
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
import jakarta.validation.constraints.Size;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/*
 * @author denijal
 */
@Entity
@Table(name = "track")
@XmlRootElement
@NamedQueries({
  @NamedQuery(name = "TrackEntity.findAll", query = "SELECT t FROM TrackEntity t"),
  @NamedQuery(
      name = "TrackEntity.findByCustomAnswer",
      query = "SELECT t FROM TrackEntity t WHERE t.customAnswer = :customAnswer")
})
public class TrackEntity implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final SongEntity E2E_SONG = new SongEntity(UUID.fromString("c041398e-8e63-40ed-8f17-d7f1ca8ca405"));

  @Id
  @Basic(optional = false)
  @NotNull
  @Lob
  @Column(name = "id")
  private UUID id;

  @Size(max = 2147483647)
  @Column(name = "custom_answer")
  private String customAnswer;

  @OneToMany(mappedBy = "trackId")
  private List<ScheduleEntity> scheduleList;

  @JoinColumn(name = "album_id", referencedColumnName = "id")
  @ManyToOne
  private AlbumEntity albumId;

  @JoinColumn(name = "song_id", referencedColumnName = "id")
  @ManyToOne
  private SongEntity songId;

  public TrackEntity() {}

  public TrackEntity(UUID id) {
    this.id = id;
  }

    public TrackEntity(E2eGameFixtureRequest.Track song) {
        this.id = UUID.randomUUID();
        this.customAnswer = song.customAnswer();
        this.songId = E2E_SONG;
    }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCustomAnswer() {
    return customAnswer;
  }

  public void setCustomAnswer(String customAnswer) {
    this.customAnswer = customAnswer;
  }

  @XmlTransient
  public List<ScheduleEntity> getScheduleList() {
    return scheduleList;
  }

  public void setScheduleList(List<ScheduleEntity> scheduleList) {
    this.scheduleList = scheduleList;
  }

  public AlbumEntity getAlbumId() {
    return albumId;
  }

  public void setAlbumId(AlbumEntity albumId) {
    this.albumId = albumId;
  }

  public SongEntity getSongId() {
    return songId;
  }

  public void setSongId(SongEntity songId) {
    this.songId = songId;
  }

  @Override
  public String toString() {
    return "com.cevapinxile.cestereg.persistence.entity.Track[ id=" + id + " ]";
  }
}
