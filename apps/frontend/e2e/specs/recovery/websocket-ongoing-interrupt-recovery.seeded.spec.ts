import { expect, test } from '@playwright/test';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import { expectSongsWelcome } from '../../utils/ws-test-assertions';

function asText(value: unknown): string {
  return JSON.stringify(value ?? {});
}

test.describe('Ongoing interrupt recovery', () => {
  test('team pause recovery', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'TEAM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const adminWelcome = expectSongsWelcome(clients.admin.frames, seed.currentScheduleId);
        const tvWelcome = expectSongsWelcome(clients.tv.frames, seed.currentScheduleId);

        expect(asText(adminWelcome)).toContain(seed.teams[0].id);
        expect(asText(tvWelcome)).toContain(seed.teams[0].id);
      } finally {
        await clients.close();
      }
    });
  });

  test('system pause recovery', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const admin = await connectRole(browser, 'admin', seed.roomCode);

      try {
        const welcome = expectSongsWelcome(admin.frames, seed.currentScheduleId);
        expect(String(welcome.scheduleId)).toBe(seed.currentScheduleId);
      } finally {
        await admin.close();
      }
    });
  });

  test('layered pause recovery', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'LAYERED_TEAM_SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const adminWelcome = expectSongsWelcome(clients.admin.frames, seed.currentScheduleId);
        const tvWelcome = expectSongsWelcome(clients.tv.frames, seed.currentScheduleId);

        expect(asText(adminWelcome)).toContain(seed.teams[0].id);
        expect(asText(tvWelcome)).toContain(seed.teams[0].id);
        expect(asText(adminWelcome)).toContain(seed.currentScheduleId);
        expect(asText(tvWelcome)).toContain(seed.currentScheduleId);
      } finally {
        await clients.close();
      }
    });
  });
});
