import { expect, test } from '@playwright/test';
import { createInterrupt, revealSchedule } from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import { expectBackendWsFrameTypeAfter, lastFrameOfType } from '../../utils/ws-capture';
import { expectSongsWelcome } from '../../utils/ws-test-assertions';

function json(frame: unknown): string {
  return JSON.stringify(frame ?? {});
}

test.describe('Recovery substates', () => {
  test('active listening recovery', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const admin = await connectRole(browser, 'admin', seed.roomCode);
      const tv = await connectRole(browser, 'tv', seed.roomCode);
      try {
        expectSongsWelcome(admin.frames, seed.currentScheduleId);
        expectSongsWelcome(tv.frames, seed.currentScheduleId);
      } finally {
        await admin.close();
        await tv.close();
      }
    });
  });

  test('revealed song recovery', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_REVEALED', async (seed) => {
      const admin = await connectRole(browser, 'admin', seed.roomCode);
      const tv = await connectRole(browser, 'tv', seed.roomCode);
      try {
        const adminWelcome = expectSongsWelcome(admin.frames, seed.currentScheduleId);
        const tvWelcome = expectSongsWelcome(tv.frames, seed.currentScheduleId);
        expect(json(adminWelcome).toLowerCase()).toContain('reveal');
        expect(json(tvWelcome).toLowerCase()).toContain('reveal');
      } finally {
        await admin.close();
        await tv.close();
      }
    });
  });

  test('team pause reconnect', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 0);
        const pause = lastFrameOfType(clients.tv.frames, 'pause')?.json;

        // A room allows only one active TV socket. Close the original TV
        // before asserting reconnect/recovery for a replacement TV.
        await clients.tv.close();

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const welcome = expectSongsWelcome(replacementTv.frames, seed.currentScheduleId);
          expect(json(welcome)).toContain(seed.teams[0].id);
          expect(json(welcome)).toContain(String(pause?.interruptId));
        } finally {
          await replacementTv.close();
        }
      } finally {
        await clients.close();
      }
    });
  });

  test('system pause reconnect', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 0);

        // A room allows only one active Admin socket. Close the original Admin
        // before asserting reconnect/recovery for a replacement Admin.
        await clients.admin.close();

        const replacementAdmin = await connectRole(browser, 'admin', seed.roomCode);
        try {
          const welcome = expectSongsWelcome(replacementAdmin.frames, seed.currentScheduleId);

          // System-pause recovery intentionally exposes the recoverable public state,
          // not the internal system interrupt id. The important WS contract is that
          // the replacement Admin sees the current schedule and error=true.
          expect(welcome?.error).toBe(true);
          expect(String(welcome?.scheduleId)).toBe(seed.currentScheduleId);
        } finally {
          await replacementAdmin.close();
        }
      } finally {
        await clients.close();
      }
    });
  });

  test('post-reveal reconnect', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await revealSchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', 0);

        // A room allows only one active TV socket. Close the original TV
        // before asserting reconnect/recovery for a replacement TV.
        await clients.tv.close();

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const welcome = expectSongsWelcome(replacementTv.frames, seed.currentScheduleId);
          expect(json(welcome).toLowerCase()).toContain('reveal');
        } finally {
          await replacementTv.close();
        }
      } finally {
        await clients.close();
      }
    });
  });
});
