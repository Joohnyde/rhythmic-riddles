import { Component, Input, OnChanges, SimpleChanges, signal } from '@angular/core';
import { AlbumArtwork } from '../../../domain/game/messages/stage2.messages';
import { getStage1AlbumImageUrl } from '../stage1-album-selection/stage1-album-image-url';

@Component({
  selector: 'rr-song-round-album',
  templateUrl: './song-round-album.component.html',
  styleUrl: './song-round-album.component.scss',
})
export class SongRoundAlbumComponent implements OnChanges {
  @Input({ required: true }) album!: AlbumArtwork;
  readonly imageFailed = signal(false);
  readonly imageUrl = getStage1AlbumImageUrl;

  ngOnChanges(changes: SimpleChanges): void {
    const album = changes['album'];
    if (album && album.previousValue?.image !== album.currentValue.image) {
      this.imageFailed.set(false);
    }
  }
}
