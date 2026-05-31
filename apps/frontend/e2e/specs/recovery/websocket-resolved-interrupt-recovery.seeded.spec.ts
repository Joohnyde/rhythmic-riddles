import { expect, test } from '@playwright/test';
import { answerInterrupt, createInterrupt, resolveSystemInterrupt } from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import { expectBackendWsFrameTypeAfter, lastFrameOfType } from '../../utils/ws-capture';
import { expectSongsWelcome } from '../../utils/ws-test-assertions';

function asText(value: unknown): string {
  return JSON.stringify(value ?? {});
}

test.describe('Resolved interrupt recovery', () => {
  test('resolved layered not active', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'RESOLVED_LAYERED_TEAM_SYSTEM', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const adminWelcome = expectSongsWelcome(clients.admin.frames, seed.currentScheduleId);
        const tvWelcome = expectSongsWelcome(clients.tv.frames, seed.currentScheduleId);

        expect(adminWelcome.error).not.toBe(true);
        expect(tvWelcome.error).not.toBe(true);

        for (const interruptId of seed.resolvedInterruptIds) {
          expect(asText(adminWelcome)).not.toContain(interruptId);
          expect(asText(tvWelcome)).not.toContain(interruptId);
        }
      } finally {
        await clients.close();
      }
    });
  });

  test('resolved replacement tv recovery', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'RESOLVED_LAYERED_TEAM_SYSTEM', async (seed) => {
      const tv = await connectRole(browser, 'tv', seed.roomCode);

      try {
        const welcome = expectSongsWelcome(tv.frames, seed.currentScheduleId);

        for (const interruptId of seed.resolvedInterruptIds) {
          expect(asText(welcome)).not.toContain(interruptId);
        }
      } finally {
        await tv.close();
      }
    });
  });

  test('answered crash no stale pause', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 0);

        const teamPause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        expect(String(teamPause?.answeringTeamId)).toBe(seed.teams[0].id);

        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 1);

        const systemPause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        expect([null, 'null']).toContain(systemPause?.answeringTeamId as null | string);

        expect(
          await answerInterrupt(request, seed.roomCode, String(teamPause?.interruptId), true),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'answer', 0);

        await clients.tv.close();

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const welcome = expectSongsWelcome(replacementTv.frames, seed.currentScheduleId);
          const welcomeText = asText(welcome);

          expect(welcome.error).not.toBe(true);
          expect(welcomeText).not.toContain(String(teamPause?.interruptId));
          expect(welcomeText).not.toContain(String(systemPause?.interruptId));
        } finally {
          await replacementTv.close();
        }
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });

  test('resolved crash no stale pause', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', 0);

        const pause = lastFrameOfType(clients.admin.frames, 'pause')?.json;

        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId!),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'error_solved', 0);

        await clients.admin.close();

        const replacementAdmin = await connectRole(browser, 'admin', seed.roomCode);
        try {
          const welcome = expectSongsWelcome(replacementAdmin.frames, seed.currentScheduleId);
          expect(asText(welcome)).not.toContain(String(pause?.interruptId));
        } finally {
          await replacementAdmin.close();
        }
      } finally {
        await clients.tv.close().catch(() => undefined);
        await clients.admin.close().catch(() => undefined);
      }
    });
  });
});
