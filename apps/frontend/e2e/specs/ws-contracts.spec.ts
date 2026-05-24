import { expect, test } from '@playwright/test';
import { LoginPage } from '../pages/login-page';
import { createRoom, createTeam, deleteTeam } from '../utils/api-client';
import {
  assertFrameMatchesBackendSchema,
  backendSchemaFiles,
  backendSchemaValidationAvailable,
  expectedBackendSchemaTypes,
  loadBackendSchemaForType,
  schemaFileByType,
} from '../utils/backend-schema-contracts';
import { assertAllBackendFramesHaveFrontendContract, knownFrontendWsTypes } from '../utils/ws-contracts';
import { backendApplicationFrames, captureReceivedWebSocketFrames, expectBackendWsFrameType } from '../utils/ws-capture';

test.describe('websocket contract validation', () => {
  test('frontend contract registry contains one schema per documented frame type', async () => {
    expect(knownFrontendWsTypes()).toEqual([
      'album_picked',
      'answer',
      'error_solved',
      'kick_team',
      'new_team',
      'pause',
      'song_next',
      'song_repeat',
      'song_reveal',
      'welcome',
    ]);
  });

  test('backend schema files are present exactly once when backend test resources are available', async () => {
    test.skip(!backendSchemaValidationAvailable(), 'Backend schema files not found from frontend project layout.');

    expect(backendSchemaFiles()).toEqual(Object.values(schemaFileByType).sort());

    for (const type of expectedBackendSchemaTypes()) {
      const schema = loadBackendSchemaForType(type) as Record<string, unknown>;
      expect(schema).toBeTruthy();
      expect(schema.type).toBe('object');
    }
  });

  test('browser-observed backend frames validate against frontend and backend contracts', async ({ page, request }) => {
    const roomCode = await createRoom(request);
    const frames = captureReceivedWebSocketFrames(page);

    await new LoginPage(page).openTv();
    await new LoginPage(page).login(roomCode);

    await expectBackendWsFrameType(frames, 'welcome');

    const team = await createTeam(request, roomCode, 'Contract Team');
    await expectBackendWsFrameType(frames, 'new_team');

    await deleteTeam(request, roomCode, team.id);
    await expectBackendWsFrameType(frames, 'kick_team');

    assertAllBackendFramesHaveFrontendContract(frames);

    if (backendSchemaValidationAvailable()) {
      for (const frame of backendApplicationFrames(frames)) {
        assertFrameMatchesBackendSchema(frame.json!);
      }
    }
  });
});
