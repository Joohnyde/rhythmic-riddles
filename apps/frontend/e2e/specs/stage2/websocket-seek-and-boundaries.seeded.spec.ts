import { expect, test } from '@playwright/test';
import { createInterrupt, resolveSystemInterrupt } from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  expectNoAdditionalFramesOfType,
  lastFrameOfType,
} from '../../utils/ws-capture';
import { expectSongsWelcome } from '../../utils/ws-test-assertions';

function maybeNumber(value: unknown): number | undefined {
  if (value === undefined || value === null) return undefined;
  const n = Number(value);
  return Number.isFinite(n) ? n : undefined;
}

test.describe('Seek boundaries', () => {
  test('expired schedule rejects buzz', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'EXPIRED_NO_PAUSE', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(
          await createInterrupt(request, seed.roomCode, seed.teams[0].id),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(clients.tv.frames, 'pause', pauseBefore);
      } finally {
        await clients.close();
      }
    });
  });

  test('resolved pause preserves window', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'RESOLVED_LONG_SYSTEM_PAUSE', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', pauseBefore);
        expect(lastFrameOfType(clients.tv.frames, 'pause')?.json?.answeringTeamId).toBe(
          seed.teams[0].id,
        );
      } finally {
        await clients.close();
      }
    });
  });

  test('overlapping pauses not double counted', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'OVERLAPPING_PAUSES_EXPIRED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(
          await createInterrupt(request, seed.roomCode, seed.teams[0].id),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(clients.tv.frames, 'pause', pauseBefore);
      } finally {
        await clients.close();
      }
    });
  });

  test('ongoing pause recovery schedule', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const admin = await connectRole(browser, 'admin', seed.roomCode);

      try {
        const welcome = expectSongsWelcome(admin.frames, seed.currentScheduleId);
        expect(String(welcome.scheduleId)).toBe(seed.currentScheduleId);

        const seek = maybeNumber(welcome.seek);
        if (seek !== undefined) {
          expect(seek).toBeLessThan(9.6);
        }
      } finally {
        await admin.close();
      }
    });
  });

  test('continue then buzz accepted', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const solvedBefore = countBackendWsFramesOfType(clients.tv.frames, 'error_solved');
        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'error_solved', solvedBefore);

        const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', pauseBefore);
      } finally {
        await clients.close();
      }
    });
  });

  test('fresh listening accepts buzz', async ({ browser, request }) => {
    await withGameFixture(
      request,
      'SONGS_LISTENING',
      { activeStartedOffsetMillis: -500 },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);

        try {
          const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
          expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', pauseBefore);
        } finally {
          await clients.close();
        }
      },
    );
  });
});
