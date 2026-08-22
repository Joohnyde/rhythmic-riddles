import fs from 'node:fs';
import path from 'node:path';
import { expect } from '@playwright/test';
import Ajv2020 from 'ajv/dist/2020';
import { CapturedWsFrame, backendReceivedApplicationFrames } from './ws-capture';

const bundledSchemaDir = path.resolve(
  process.cwd(),
  'e2e/contracts/backend/websocket-contracts/v1/schema',
);
const backendWorkspaceSchemaDir = path.resolve(
  process.cwd(),
  '../backend/src/test/resources/websocket-contracts/v1/schema',
);

const schemaFileByType: Record<string, string> = {
  welcome: 'welcome.schema.json',
  new_team: 'new_team.schema.json',
  kick_team: 'kick_team.schema.json',
  button_clicked: 'button_clicked.schema.json',
  album_picked: 'album_picked.schema.json',
  pause: 'pause.schema.json',
  answer: 'answer.schema.json',
  error_solved: 'error_solved.schema.json',
  song_repeat: 'song_repeat.schema.json',
  song_reveal: 'song_reveal.schema.json',
  song_next: 'song_next.schema.json',
};

const candidateSchemaDirs = [
  // Prefer the schema files packaged with this test iteration. This avoids accidentally
  // validating against stale local backend test resources.
  bundledSchemaDir,
  backendWorkspaceSchemaDir,
];

const ajv = new Ajv2020({ allErrors: true, strict: false });
const validators = new Map<string, ReturnType<typeof ajv.compile>>();

export function backendSchemaDir(): string | undefined {
  return candidateSchemaDirs.find((dir) => fs.existsSync(dir));
}

export function backendSchemaValidationAvailable(): boolean {
  return Boolean(backendSchemaDir());
}

export function knownBackendSchemaTypes(): string[] {
  return Object.keys(schemaFileByType).sort();
}

export function loadBackendSchemaForType(type: string): object {
  const dir = backendSchemaDir();
  expect(dir, 'backend websocket schema directory should exist').toBeTruthy();

  const file = schemaFileByType[type];
  expect(file, `no backend schema mapping registered for ${type}`).toBeTruthy();

  const fullPath = path.join(dir!, file);
  expect(fs.existsSync(fullPath), `missing backend schema file ${fullPath}`).toBeTruthy();

  return JSON.parse(fs.readFileSync(fullPath, 'utf-8'));
}

export function loadPublishedFrameRegistry(): { publishedFrameTypes: string[] } {
  const dir = backendSchemaDir();
  expect(dir, 'backend websocket schema directory should exist').toBeTruthy();

  const fullPath = path.join(dir!, '_published-frame-registry.schema.json');
  expect(fs.existsSync(fullPath), `missing backend schema registry ${fullPath}`).toBeTruthy();

  const registrySchema = JSON.parse(fs.readFileSync(fullPath, 'utf-8'));
  const enums = registrySchema?.properties?.publishedFrameTypes?.items?.enum;
  expect(Array.isArray(enums), 'published frame registry should expose enum types').toBeTruthy();

  return { publishedFrameTypes: [...enums].sort() };
}

export function assertKnownBackendSchemasAreLoadable(): void {
  for (const type of knownBackendSchemaTypes()) {
    loadBackendSchemaForType(type);
  }
}

export function assertFrameMatchesBackendSchema(frame: Record<string, unknown>): void {
  const type = String(frame.type);
  let validate = validators.get(type);

  if (!validate) {
    validate = ajv.compile(loadBackendSchemaForType(type));
    validators.set(type, validate);
  }

  const valid = validate(frame);
  expect(
    valid,
    `invalid backend websocket schema frame ${type}: ${ajv.errorsText(validate.errors)}`,
  ).toBeTruthy();
}

export function assertObservedFramesMatchBackendSchemas(frames: CapturedWsFrame[]): void {
  for (const frame of backendReceivedApplicationFrames(frames)) {
    assertFrameMatchesBackendSchema(frame.json!);
  }
}
