import { expect } from '@playwright/test';
import { CapturedWsFrame, lastFrameOfType, observedBackendTypes } from './ws-capture';

export function normalized(value: unknown): string {
  return String(value)
    .toLowerCase()
    .replace(/[_\s-]/g, '');
}

export function expectSongsWelcome(
  frames: CapturedWsFrame[],
  scheduleId?: string,
): Record<string, unknown> {
  const welcome = lastFrameOfType(frames, 'welcome')?.json;
  expect(welcome, 'expected welcome frame').toBeTruthy();
  expect(['songs', 'song', 'listening', '2']).toContain(normalized(welcome!.stage));
  if (scheduleId) {
    expect(JSON.stringify(welcome), `welcome should reference schedule ${scheduleId}`).toContain(
      scheduleId,
    );
  }
  return welcome!;
}

export function expectFrameOrder(frames: CapturedWsFrame[], first: string, second: string): void {
  const types = observedBackendTypes(frames);
  expect(
    types.lastIndexOf(first),
    `expected ${first} before ${second}: ${types.join(',')}`,
  ).toBeGreaterThanOrEqual(0);
  expect(
    types.lastIndexOf(second),
    `expected ${second} after ${first}: ${types.join(',')}`,
  ).toBeGreaterThanOrEqual(0);
  expect(types.lastIndexOf(first)).toBeLessThan(types.lastIndexOf(second));
}

export function expectUuid(value: unknown): asserts value is string {
  expect(String(value)).toMatch(
    /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,
  );
}
