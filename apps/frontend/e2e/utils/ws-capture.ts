import { expect, Page, WebSocket } from '@playwright/test';
import { BACKEND_URL, Role, wsUrlFor } from './env';

export type CapturedWsFrame = {
  url: string;
  direction: 'sent' | 'received';
  raw: string;
  json?: Record<string, unknown>;
  timestamp: number;
};

function parseJson(payload: string): Record<string, unknown> | undefined {
  try {
    const parsed = JSON.parse(payload);
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, unknown>) : undefined;
  } catch {
    return undefined;
  }
}

function backendWsPrefix(): string {
  const backend = new URL(BACKEND_URL);
  const protocol = backend.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${backend.host}/ws/`;
}

export function captureWebSocketFrames(page: Page): CapturedWsFrame[] {
  const frames: CapturedWsFrame[] = [];

  page.on('websocket', (ws: WebSocket) => {
    ws.on('framesent', (event) => {
      frames.push({
        url: ws.url(),
        direction: 'sent',
        raw: event.payload,
        json: parseJson(event.payload),
        timestamp: Date.now(),
      });
    });

    ws.on('framereceived', (event) => {
      frames.push({
        url: ws.url(),
        direction: 'received',
        raw: event.payload,
        json: parseJson(event.payload),
        timestamp: Date.now(),
      });
    });
  });

  return frames;
}

export const captureReceivedWebSocketFrames = captureWebSocketFrames;

export function backendWebSocketFrames(frames: CapturedWsFrame[]): CapturedWsFrame[] {
  const prefix = backendWsPrefix();
  return frames.filter((frame) => frame.url.startsWith(prefix));
}

export function backendApplicationFrames(frames: CapturedWsFrame[]): CapturedWsFrame[] {
  return backendWebSocketFrames(frames).filter(
    (frame) =>
      frame.direction === 'received' &&
      frame.json &&
      typeof frame.json.type === 'string',
  );
}

export function backendSentApplicationFrames(frames: CapturedWsFrame[]): CapturedWsFrame[] {
  return backendWebSocketFrames(frames).filter(
    (frame) =>
      frame.direction === 'sent' &&
      frame.json &&
      typeof frame.json.type === 'string',
  );
}

export function backendFramesOfType(frames: CapturedWsFrame[], type: string): CapturedWsFrame[] {
  return backendApplicationFrames(frames).filter((frame) => frame.json?.type === type);
}

export function countBackendWsFramesOfType(frames: CapturedWsFrame[], type: string): number {
  return backendFramesOfType(frames, type).length;
}

export async function expectBackendWsFrameType(
  frames: CapturedWsFrame[],
  type: string,
  timeout = 10_000,
): Promise<void> {
  await expect
    .poll(() => countBackendWsFramesOfType(frames, type), {
      timeout,
      message: `expected backend websocket frame of type ${type}`,
    })
    .toBeGreaterThanOrEqual(1);
}

export async function expectNoAdditionalFramesOfType(
  frames: CapturedWsFrame[],
  type: string,
  baseline: number,
  settleMillis = 750,
): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, settleMillis));
  expect(countBackendWsFramesOfType(frames, type)).toBe(baseline);
}

export function backendWsUrls(frames: CapturedWsFrame[]): string[] {
  return [...new Set(backendWebSocketFrames(frames).map((frame) => frame.url))];
}

export function hasExpectedRoleWsUrl(frames: CapturedWsFrame[], role: Role, roomCode: string): boolean {
  return backendWsUrls(frames).includes(wsUrlFor(role, roomCode));
}

export function observedBackendTypes(frames: CapturedWsFrame[]): string[] {
  return backendApplicationFrames(frames).map((frame) => String(frame.json!.type));
}

export function lastFrameOfType(frames: CapturedWsFrame[], type: string): CapturedWsFrame | undefined {
  const matches = backendFramesOfType(frames, type);
  return matches[matches.length - 1];
}

export async function settle(ms = 750): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}
