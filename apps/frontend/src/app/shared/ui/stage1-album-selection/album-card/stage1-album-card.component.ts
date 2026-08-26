import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { AlbumCardVm } from '../../../../domain/game/models/album.model';

@Component({
  selector: 'rr-stage1-album-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stage1-album-card.component.html',
  styleUrl: './stage1-album-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Stage1AlbumCardComponent {
  readonly album = input.required<AlbumCardVm>();
  readonly imageUrl = input.required<string>();
  readonly disabled = input(false);
  readonly selected = input(false);
  readonly glow = input(false);
  readonly interactive = input(false);
  readonly testId = input<string | null>(null);
  readonly clickCard = output<string>();
  readonly frameVariant = computed(() => {
    const key = `${this.album().id}:${this.album().name}`;
    let hash = 0;
    for (const character of key) {
      hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
    }
    return hash % 4;
  });

  handleClick(): void {
    if (!this.disabled()) {
      this.clickCard.emit(this.album().id);
    }
  }
}
