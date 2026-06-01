import { expect, test } from '@playwright/test';
import { answerInterrupt, createInterrupt, resolveSystemInterrupt } from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  expectNoAdditionalFramesOfType,
  lastFrameOfType,
  settle,
} from '../../utils/ws-capture';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';
import { expectFrameOrder, expectUuid } from '../../utils/ws-test-assertions';

test.describe('Interrupt stack', () => {
  test('team pause blocks team buzz', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const tvPauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');

        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', tvPauseBefore);

        const afterFirstPause = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(
          await createInterrupt(request, seed.roomCode, seed.teams[1].id),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(clients.tv.frames, 'pause', afterFirstPause);

        const pause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        expect(pause?.answeringTeamId).toBe(seed.teams[0].id);
        expectUuid(pause?.interruptId);
      } finally {
        await clients.close();
      }
    });
  });

  test('system pause blocks team buzz', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const tvPauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');

        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', tvPauseBefore);

        const systemPause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        expect([null, 'null']).toContain(systemPause?.answeringTeamId as null | string);

        const afterSystemPause = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(
          await createInterrupt(request, seed.roomCode, seed.teams[0].id),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(clients.tv.frames, 'pause', afterSystemPause);
      } finally {
        await clients.close();
      }
    });
  });

  test('layered pause resolves answer', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const adminPauseBefore = countBackendWsFramesOfType(clients.admin.frames, 'pause');
        const tvPauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');

        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', adminPauseBefore);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', tvPauseBefore);

        const teamPause = lastFrameOfType(clients.admin.frames, 'pause')?.json;
        expect(teamPause?.answeringTeamId).toBe(seed.teams[0].id);
        expectUuid(teamPause?.interruptId);

        const afterTeamPause = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', afterTeamPause);

        const systemPause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        expect([null, 'null']).toContain(systemPause?.answeringTeamId as null | string);

        const adminAnswerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');
        const tvAnswerBefore = countBackendWsFramesOfType(clients.tv.frames, 'answer');
        expect(
          await answerInterrupt(request, seed.roomCode, String(teamPause?.interruptId), true),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'answer', adminAnswerBefore);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'answer', tvAnswerBefore);

        const answer = lastFrameOfType(clients.tv.frames, 'answer')?.json;
        expect(answer?.teamId).toBe(seed.teams[0].id);
        expect(answer?.correct).toBe(true);
        expect(String(answer?.scheduleId)).toBe(seed.currentScheduleId);
        expectFrameOrder(clients.tv.frames, 'pause', 'answer');
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('system pauses resolve once', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', pauseBefore);

        const pauseAfterFirst = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        const secondStatus = await createInterrupt(request, seed.roomCode, null);
        expect(secondStatus).toBeLessThan(500);

        if (secondStatus < 400) {
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', pauseAfterFirst);
        }

        const solvedBefore = countBackendWsFramesOfType(clients.tv.frames, 'error_solved');
        const answerBefore = countBackendWsFramesOfType(clients.tv.frames, 'answer');
        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId!),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'error_solved', solvedBefore);
        await settle();
        expect(countBackendWsFramesOfType(clients.tv.frames, 'answer')).toBe(answerBefore);
        // Do not contract-validate this live-created system-pause path: previousScenario
        // is saved by clients after receiving pause(null), so this test is only about
        // stack behavior/no duplicate answer frame.
      } finally {
        await clients.close();
      }
    });
  });

  test('layered pause recovery schedule', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 0);
        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);

        // A room allows only one active Admin socket. Close the original Admin
        // before asserting reconnect/recovery for a replacement Admin.
        await clients.admin.close();

        const replacementAdmin = await connectRole(browser, 'admin', seed.roomCode);
        try {
          const welcome = lastFrameOfType(replacementAdmin.frames, 'welcome')?.json;
          expect(JSON.stringify(welcome)).toContain(seed.currentScheduleId);
          expect(JSON.stringify(welcome)).toContain(seed.teams[0].id);
        } finally {
          await replacementAdmin.close();
        }
      } finally {
        await clients.close();
      }
    });
  });
});
