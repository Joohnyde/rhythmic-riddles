import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LobbyStore } from '../../../../../domain/game/state/lobby.store';
import { BrandLetteringComponent } from '../../../../../shared/ui/brand-lettering/brand-lettering.component';

@Component({
  selector: 'rr-admin-team-form',
  imports: [FormsModule, BrandLetteringComponent],
  templateUrl: './admin-team-form.component.html',
  styleUrl: './admin-team-form.component.scss',
})
export class AdminTeamFormComponent {
  readonly store = inject(LobbyStore);
  readonly buzzerPulsing = signal(false);

  private readonly destroyRef = inject(DestroyRef);
  private lastBuzzerPulseSequence = 0;
  private restartFrame?: number;

  constructor() {
    effect(() => {
      const vm = this.store.vm();
      const sequence = vm.buzzerPulseSequence;

      if (!vm.draft.buttonCode) {
        this.stopBuzzerPulse();
        return;
      }
      if (sequence === 0 || sequence === this.lastBuzzerPulseSequence) {
        return;
      }

      this.lastBuzzerPulseSequence = sequence;
      this.restartBuzzerPulse();
    });

    this.destroyRef.onDestroy(() => this.stopBuzzerPulse());
  }

  createTeam(): void {
    void this.store.createTeam();
  }

  private restartBuzzerPulse(): void {
    this.stopBuzzerPulse();

    // Removing the class first lets every subsequent buzzer frame restart the CSS animation,
    // even when frames arrive faster than the animation itself can finish.
    this.restartFrame = window.requestAnimationFrame(() => {
      this.restartFrame = undefined;
      this.buzzerPulsing.set(true);
    });
  }

  private stopBuzzerPulse(): void {
    if (this.restartFrame !== undefined) {
      window.cancelAnimationFrame(this.restartFrame);
      this.restartFrame = undefined;
    }
    this.buzzerPulsing.set(false);
  }
}
