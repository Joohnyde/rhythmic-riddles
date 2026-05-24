import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TeamScore } from '../../../../domain/game/models/team-score.model';
import { AudioPlayerComponent } from '../../audio-player/audio-player.component';

@Component({
  selector: 'rr-song-revealed-panel',
  imports: [AudioPlayerComponent],
  templateUrl: './song-revealed-panel.component.html',
  styleUrl: './song-revealed-panel.component.scss',
})
export class SongRevealedPanelComponent {
  @Input() answer: string | null = null;
  @Input() bravo: TeamScore | null = null;

  /**
   * Optional on purpose:
   * - TV passes backend answer audio URL and duration.
   * - Admin does not pass audio because original Admin Stage 2 only used controls/timer.
   */
  @Input() answerAudioSrc: string | null = null;
  @Input() answerDuration: number | null = null;

  @Output() answerCompleted = new EventEmitter<void>();
}
