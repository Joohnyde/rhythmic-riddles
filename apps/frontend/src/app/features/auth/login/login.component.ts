import { ChangeDetectionStrategy, Component, Input, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { GameRealtimeService } from '../../../core/realtime/game-realtime.service';
import { GameSession } from '../../../core/session/game-session.service';
import { ClientSurface } from '../../../domain/game/models/client-surface.model';
import { routeForStage } from '../../../domain/game/models/game-stage.model';

@Component({
  selector: 'rr-login',
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  @Input({ required: true }) surface!: ClientSurface;

  private readonly realtime = inject(GameRealtimeService);
  private readonly session = inject(GameSession);
  private readonly router = inject(Router);

  readonly roomCode = signal('');
  readonly roomCodeValid = computed(() => /^[A-Za-z]{4}$/.test(this.roomCode().trim()));
  readonly inTransit = signal(false);
  readonly error = signal<string | null>(null);

  async login(): Promise<void> {
    const code = this.roomCode().trim().toUpperCase();
    if (!this.roomCodeValid() || this.inTransit()) {
      return;
    }

    this.roomCode.set(code);
    this.inTransit.set(true);
    this.error.set(null);

    try {
      const messages$ = this.realtime.connect({ roomCode: code, surface: this.surface });
      const first = await firstValueFrom(messages$);
      if (first.type !== 'welcome') {
        throw new Error(`Expected welcome frame, received ${first.type}`);
      }

      this.session.code = code;
      this.session.messages$ = messages$;
      const navigated = await this.router.navigate(routeForStage(this.surface, first.stage));
      if (!navigated) {
        throw new Error('Navigation after welcome was cancelled');
      }
    } catch {
      this.realtime.disconnect();
      this.session.clear();
      this.error.set('Could not connect to that room.');
    } finally {
      this.inTransit.set(false);
    }
  }
}
