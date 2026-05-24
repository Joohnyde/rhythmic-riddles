import { expect, test } from '@playwright/test';
import {
  answerInterrupt,
  createInterrupt,
  nextSchedule,
  replaySchedule,
  resolveSystemInterrupt,
  revealSchedule,
} from '../utils/api-client';
import { seedStage2Room } from '../utils/stage2-seeder';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameType,
  lastFrameOfType,
  observedBackendTypes,
  settle,
} from '../utils/ws-capture';
import { assertAllBackendFramesHaveFrontendContract } from '../utils/ws-contracts';

test.describe('self-seeded Stage-2 websocket integration', () => {
  test('self-seeds a real songs-stage room and receives songs welcome on Admin and TV', async ({ browser, request }) => {
    const ctx = await seedStage2Room(browser, request);

    try {
      const adminSongs =
        lastFrameOfType(ctx.adminFrames, 'song_next')?.json ??
        lastFrameOfType(ctx.adminFrames, 'welcome')?.json;

      const tvSongs =
        lastFrameOfType(ctx.tvFrames, 'song_next')?.json ??
        lastFrameOfType(ctx.tvFrames, 'welcome')?.json;

      expect(adminSongs?.scheduleId).toBeTruthy();
      expect(tvSongs?.scheduleId).toBeTruthy();

      assertAllBackendFramesHaveFrontendContract(ctx.adminFrames);
      assertAllBackendFramesHaveFrontendContract(ctx.tvFrames);
    } finally {
      await ctx.close();
    }
  });

  test('repeat then reveal are broadcast once and in order', async ({ browser, request }) => {
    const ctx = await seedStage2Room(browser, request);

    try {
      const beforeRepeatTv = countBackendWsFramesOfType(ctx.tvFrames, 'song_repeat');
      const beforeRevealTv = countBackendWsFramesOfType(ctx.tvFrames, 'song_reveal');

      const replayStatus = await replaySchedule(request, ctx.roomCode, ctx.scheduleId);
      expect(replayStatus).toBeLessThan(400);

      await expectBackendWsFrameType(ctx.adminFrames, 'song_repeat');
      await expectBackendWsFrameType(ctx.tvFrames, 'song_repeat');

      expect(countBackendWsFramesOfType(ctx.tvFrames, 'song_repeat')).toBe(beforeRepeatTv + 1);

      const revealStatus = await revealSchedule(request, ctx.roomCode, ctx.scheduleId);
      expect(revealStatus).toBeLessThan(400);

      await expectBackendWsFrameType(ctx.adminFrames, 'song_reveal');
      await expectBackendWsFrameType(ctx.tvFrames, 'song_reveal');

      expect(countBackendWsFramesOfType(ctx.tvFrames, 'song_reveal')).toBe(beforeRevealTv + 1);

      const types = observedBackendTypes(ctx.tvFrames);
      expect(types.lastIndexOf('song_repeat')).toBeLessThan(types.lastIndexOf('song_reveal'));

      assertAllBackendFramesHaveFrontendContract(ctx.adminFrames);
      assertAllBackendFramesHaveFrontendContract(ctx.tvFrames);
    } finally {
      await ctx.close();
    }
  });

  test('team pause and correct answer are broadcast to Admin and TV with interrupt id from pause frame', async ({ browser, request }) => {
    const ctx = await seedStage2Room(browser, request);

    try {
      const pauseStatus = await createInterrupt(request, ctx.roomCode, ctx.teamId);
      expect(pauseStatus).toBeLessThan(400);

      await expectBackendWsFrameType(ctx.adminFrames, 'pause');
      await expectBackendWsFrameType(ctx.tvFrames, 'pause');

      const pause = lastFrameOfType(ctx.adminFrames, 'pause')?.json;
      expect(pause?.answeringTeamId).toBe(ctx.teamId);
      expect(typeof pause?.interruptId).toBe('string');

      const answerStatus = await answerInterrupt(request, ctx.roomCode, pause!.interruptId as string, true);
      expect(answerStatus).toBeLessThan(400);

      await expectBackendWsFrameType(ctx.adminFrames, 'answer');
      await expectBackendWsFrameType(ctx.tvFrames, 'answer');

      const answer = lastFrameOfType(ctx.tvFrames, 'answer')?.json;
      expect(answer?.teamId).toBe(ctx.teamId);
      expect(answer?.scheduleId).toBe(ctx.scheduleId);
      expect(answer?.correct).toBe(true);

      assertAllBackendFramesHaveFrontendContract(ctx.adminFrames);
      assertAllBackendFramesHaveFrontendContract(ctx.tvFrames);
    } finally {
      await ctx.close();
    }
  });

  test('system pause and resolve emit pause(null) then error_solved', async ({ browser, request }) => {
    const ctx = await seedStage2Room(browser, request);

    try {
      const pauseStatus = await createInterrupt(request, ctx.roomCode, null);
      expect(pauseStatus).toBeLessThan(400);

      await expectBackendWsFrameType(ctx.adminFrames, 'pause');
      await expectBackendWsFrameType(ctx.tvFrames, 'pause');

      const pause = lastFrameOfType(ctx.tvFrames, 'pause')?.json;
      expect([null, 'null']).toContain(pause?.answeringTeamId as string | null);

      const resolveStatus = await resolveSystemInterrupt(request, ctx.roomCode, ctx.scheduleId);
      expect(resolveStatus).toBeLessThan(400);

      await expectBackendWsFrameType(ctx.adminFrames, 'error_solved');
      await expectBackendWsFrameType(ctx.tvFrames, 'error_solved');

      assertAllBackendFramesHaveFrontendContract(ctx.adminFrames);
      assertAllBackendFramesHaveFrontendContract(ctx.tvFrames);
    } finally {
      await ctx.close();
    }
  });

  test('next schedule emits either song_next or stage recovery welcome to both clients', async ({ browser, request }) => {
    const ctx = await seedStage2Room(browser, request);

    try {
      const beforeAdminNext = countBackendWsFramesOfType(ctx.adminFrames, 'song_next');
      const beforeTvNext = countBackendWsFramesOfType(ctx.tvFrames, 'song_next');
      const beforeAdminWelcome = countBackendWsFramesOfType(ctx.adminFrames, 'welcome');
      const beforeTvWelcome = countBackendWsFramesOfType(ctx.tvFrames, 'welcome');

      const nextStatus = await nextSchedule(request, ctx.roomCode);
      expect(nextStatus).toBeLessThan(400);

      await expect
        .poll(() =>
          countBackendWsFramesOfType(ctx.adminFrames, 'song_next') > beforeAdminNext ||
          countBackendWsFramesOfType(ctx.adminFrames, 'welcome') > beforeAdminWelcome
        )
        .toBeTruthy();

      await expect
        .poll(() =>
          countBackendWsFramesOfType(ctx.tvFrames, 'song_next') > beforeTvNext ||
          countBackendWsFramesOfType(ctx.tvFrames, 'welcome') > beforeTvWelcome
        )
        .toBeTruthy();

      assertAllBackendFramesHaveFrontendContract(ctx.adminFrames);
      assertAllBackendFramesHaveFrontendContract(ctx.tvFrames);
    } finally {
      await ctx.close();
    }
  });

  test('invalid stage-2 identifiers do not emit false song or answer frames', async ({ browser, request }) => {
    const ctx = await seedStage2Room(browser, request);

    try {
      const beforeRepeat = countBackendWsFramesOfType(ctx.tvFrames, 'song_repeat');
      const beforeReveal = countBackendWsFramesOfType(ctx.tvFrames, 'song_reveal');
      const beforeAnswer = countBackendWsFramesOfType(ctx.tvFrames, 'answer');

      const badId = '00000000-0000-0000-0000-000000000000';

      await replaySchedule(request, ctx.roomCode, badId);
      await revealSchedule(request, ctx.roomCode, badId);
      await answerInterrupt(request, ctx.roomCode, badId, true);
      await settle();

      expect(countBackendWsFramesOfType(ctx.tvFrames, 'song_repeat')).toBe(beforeRepeat);
      expect(countBackendWsFramesOfType(ctx.tvFrames, 'song_reveal')).toBe(beforeReveal);
      expect(countBackendWsFramesOfType(ctx.tvFrames, 'answer')).toBe(beforeAnswer);
    } finally {
      await ctx.close();
    }
  });
});
