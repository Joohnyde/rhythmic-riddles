import { expect, test } from '@playwright/test';
import { LoginPage } from '../../pages/login-page';
import { withGameFixture } from '../../utils/fixture-api';
import { connectRole } from '../../utils/e2e-session';
import {
  captureReceivedWebSocketFrames,
  countBackendWsFramesOfType,
  expectBackendWsFrameType,
} from '../../utils/ws-capture';

/** Real REST + WebSocket handshake, with a deterministic browser media clock. */
test.describe('TV playback acknowledgement', () => {
  test('Admin waits for TV registration and media progress, then finishes on its own timer', async ({
    browser,
    request,
  }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const tvContext = await browser.newContext();
      let releaseRegistration: (() => void) | undefined;
      const registrationGate = new Promise<void>((resolve) => {
        releaseRegistration = resolve;
      });
      let registrations = 0;
      let acknowledgements = 0;
      try {
        // The AudioPlayerService owns an unattached HTMLAudioElement. Replace only
        // browser media timing; REST, the Spring backend and sockets stay real.
        await tvContext.addInitScript(() => {
          const positions = new WeakMap<HTMLMediaElement, number>();
          Object.defineProperty(HTMLMediaElement.prototype, 'currentTime', {
            configurable: true,
            get(this: HTMLMediaElement) {
              return positions.get(this) ?? 0;
            },
            set(this: HTMLMediaElement, seconds: number) {
              positions.set(this, seconds);
            },
          });
          HTMLMediaElement.prototype.load = function () {};
          HTMLMediaElement.prototype.pause = function () {};
          HTMLMediaElement.prototype.play = function () {
            this.dispatchEvent(new Event('playing'));
            let elapsed = 0;
            const tick = window.setInterval(() => {
              elapsed += 0.05;
              positions.set(this, elapsed);
              this.dispatchEvent(new Event('timeupdate'));
              if (elapsed >= 10) {
                window.clearInterval(tick);
                this.dispatchEvent(new Event('ended'));
              }
            }, 50);
            return Promise.resolve();
          };
        });
        const tvPage = await tvContext.newPage();
        const tvFrames = captureReceivedWebSocketFrames(tvPage);
        await tvPage.route('**/api/v1/games/*/delays**', async (route) => {
          if (route.request().method() === 'POST') {
            registrations++;
            await registrationGate;
          } else if (route.request().method() === 'PUT') {
            acknowledgements++;
          }
          await route.continue();
        });

        const adminPromise = connectRole(browser, 'admin', seed.roomCode);
        const tvLogin = new LoginPage(tvPage);
        const tvPromise = (async () => {
          await tvLogin.open('tv');
          await tvLogin.login('tv', seed.roomCode);
          await tvLogin.expectConnected('tv');
          await expectBackendWsFrameType(tvFrames, 'welcome');
        })();
        const [admin] = await Promise.all([adminPromise, tvPromise]);
        try {
          await expect.poll(() => registrations).toBe(1);
          await expect(admin.page.locator('rr-song-loading-panel')).toBeVisible();
          expect(countBackendWsFramesOfType(admin.frames, 'play')).toBe(0);
          expect(acknowledgements).toBe(0);

          // TV must not call play() until REST registration has completed.
          releaseRegistration?.();
          await expect.poll(() => acknowledgements).toBe(1);
          await expectBackendWsFrameType(admin.frames, 'play');
          await expect(admin.page.locator('rr-seek-timer')).toBeVisible();
          expect(countBackendWsFramesOfType(tvFrames, 'play')).toBe(0);

          // No TV-completion endpoint/message exists: the Admin owns completion.
          await expect(admin.page.locator('rr-song-finished-panel')).toBeVisible({
            timeout: 16000,
          });
          expect(countBackendWsFramesOfType(admin.frames, 'play')).toBe(1);
        } finally {
          await admin.close();
        }
      } finally {
        releaseRegistration?.();
        await tvContext.close();
      }
    });
  });
});
