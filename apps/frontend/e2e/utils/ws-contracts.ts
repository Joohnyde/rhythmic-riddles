import { expect } from '@playwright/test';
import { GAME_MESSAGE_TYPES } from '../../src/app/domain/game/messages/default.messages';
import { CapturedWsFrame, backendReceivedApplicationFrames } from './ws-capture';
import {
  assertFrameMatchesBackendSchema,
  knownBackendSchemaTypes,
} from './backend-schema-governance';

export function knownFrontendWsTypes(): string[] {
  return [...GAME_MESSAGE_TYPES].sort();
}

export function assertFrontendWsContract(frame: Record<string, unknown>): void {
  const type = frame.type;
  expect(typeof type, 'websocket frame type must be string').toBe('string');
  expect(
    knownFrontendWsTypes(),
    `frontend does not register websocket frame ${String(type)}`,
  ).toContain(type as string);
  expect(knownBackendSchemaTypes(), `no websocket schema registered for ${String(type)}`).toContain(
    type as string,
  );

  // Browser-observed frames must match the shared schema files packaged under
  // e2e/contracts/backend/websocket-contracts/v1/schema.
  assertFrameMatchesBackendSchema(frame);
}

export function assertAllBackendFramesHaveFrontendContract(frames: CapturedWsFrame[]): void {
  for (const frame of backendReceivedApplicationFrames(frames)) {
    assertFrontendWsContract(frame.json!);
  }
}
