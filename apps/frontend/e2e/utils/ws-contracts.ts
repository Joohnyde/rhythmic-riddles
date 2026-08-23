import { expect } from '@playwright/test';
import { CapturedWsFrame, backendReceivedApplicationFrames } from './ws-capture';
import {
  assertFrameMatchesBackendSchema,
  knownBackendSchemaTypes,
  loadPublishedFrameRegistry,
} from './backend-schema-governance';

export function knownFrontendWsTypes(): string[] {
  // The frontend contract runner uses the bundled backend schema registry.
  // Schema-governance tests keep that checked-in copy synchronized with the backend source.
  return loadPublishedFrameRegistry().publishedFrameTypes.sort();
}

export function assertFrontendWsContract(frame: Record<string, unknown>): void {
  const type = frame.type;
  expect(typeof type, 'websocket frame type must be string').toBe('string');
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
