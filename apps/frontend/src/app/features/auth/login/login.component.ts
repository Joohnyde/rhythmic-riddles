import { ChangeDetectionStrategy, Component, Input, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { GameRealtimeService } from '../../../core/realtime/game-realtime.service';
import { GameSession } from '../../../core/session/game-session.service';
import { WelcomeMessage } from '../../../domain/game/messages/default.messages';
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
  readonly inTransit = signal(false);
  readonly error = signal<string | null>(null);

  async login(): Promise<void> {
    const code = this.roomCode().trim();
    if (!code || this.inTransit()) {
      return;
    }

    this.inTransit.set(true);
    this.error.set(null);

    const messages$ = this.realtime.connect({ roomCode: code, surface: this.surface });
    this.session.messages$ = messages$;

    try {
      const first = (await firstValueFrom(messages$)) as WelcomeMessage;
      if (first.type === 'welcome') {
        this.session.code = code;
        await this.router.navigate(routeForStage(this.surface, first.stage));
      }
    } catch {
      this.error.set('Could not connect to that room.');
    } finally {
      this.inTransit.set(false);
    }
  }
}
