import { expect, test } from '@playwright/test';
import {
  answerInterrupt,
  createInterrupt,
  nextSchedule,
  pickAlbum,
  replaySchedule,
  resolveSystemInterrupt,
  revealSchedule,
} from '../../utils/api-client';
import { connectAdminAndTv } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
  observedBackendTypes,
} from '../../utils/ws-capture';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';

type CapturedFrame = Record<string, unknown>;

type SocketFrameClient = {
  frames: CapturedFrame[];
};

type SocketClients = {
  admin?: SocketFrameClient;
  tv?: SocketFrameClient;
};

function recordTypes(observed: Set<string>, clients: SocketClients) {
  if (clients.admin)
    for (const type of observedBackendTypes(clients.admin.frames)) observed.add(type);
  if (clients.tv) for (const type of observedBackendTypes(clients.tv.frames)) observed.add(type);
}

test.describe('Runtime contract governance', () => {
  test('runtime frame coverage', async ({ browser, request }) => {
    const observed = new Set<string>();

    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        expect(await replaySchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_repeat', 0);

        recordTypes(observed, clients);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'error_solved', 0);

        recordTypes(observed, clients);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 0);

        const pause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        expect(
          await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), false),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'answer', 0);

        recordTypes(observed, clients);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    await withGameFixture(
      request,
      'SONGS_LISTENING',
      { activeStartedOffsetMillis: -12_000 },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);

        try {
          expect(
            await revealSchedule(request, seed.roomCode, seed.currentScheduleId!),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', 0);

          recordTypes(observed, clients);
          assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
          assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
        } finally {
          await clients.close();
        }
      },
    );

    await withGameFixture(request, 'SONGS_REVEALED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const nextBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_next');
        const welcomeBefore = countBackendWsFramesOfType(clients.tv.frames, 'welcome');

        expect(await nextSchedule(request, seed.roomCode)).toBeLessThan(400);

        await expect
          .poll(
            () =>
              countBackendWsFramesOfType(clients.tv.frames, 'song_next') > nextBefore ||
              countBackendWsFramesOfType(clients.tv.frames, 'welcome') > welcomeBefore,
          )
          .toBeTruthy();

        recordTypes(observed, clients);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    await withGameFixture(request, 'ALBUMS', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const beforePicked = countBackendWsFramesOfType(clients.tv.frames, 'album_picked');
        expect(
          await pickAlbum(request, seed.roomCode, seed.categories[0].id, seed.teams[0].id),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'album_picked', beforePicked);

        recordTypes(observed, clients);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    const mustObserve = [
      'welcome',
      'pause',
      'answer',
      'error_solved',
      'song_repeat',
      'song_reveal',
      'album_picked',
    ];

    for (const type of mustObserve) {
      expect([...observed], `expected browser-level WS coverage for ${type}`).toContain(type);
    }
  });
});
