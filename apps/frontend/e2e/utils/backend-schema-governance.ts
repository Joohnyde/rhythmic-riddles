import fs from 'node:fs';
import path from 'node:path';
import { expect } from '@playwright/test';
import Ajv2020 from 'ajv/dist/2020';

const bundledSchemaDir = path.resolve(
  process.cwd(),
  'e2e/contracts/backend/websocket-contracts/v1/schema',
);
const backendWorkspaceSchemaDir = path.resolve(
  process.cwd(),
  '../backend/src/test/resources/websocket-contracts/v1/schema',
);

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

export function knownBackendSchemaTypes(): string[] {
  return loadPublishedFrameRegistry().publishedFrameTypes;
}

export function loadBackendSchemaForType(type: string): object {
  const dir = backendSchemaDir();
  expect(dir, 'backend websocket schema directory should exist').toBeTruthy();

  expect(knownBackendSchemaTypes(), `no published backend schema registered for ${type}`).toContain(
    type,
  );
  const file = `${type}.schema.json`;
  const fullPath = path.join(dir!, file);
  expect(fs.existsSync(fullPath), `missing backend schema file ${fullPath}`).toBeTruthy();

  return JSON.parse(fs.readFileSync(fullPath, 'utf-8'));
}

export function assertBundledSchemasMatchBackendSource(): void {
  expect(
    fs.existsSync(bundledSchemaDir),
    `missing bundled schema directory ${bundledSchemaDir}`,
  ).toBeTruthy();
  expect(
    fs.existsSync(backendWorkspaceSchemaDir),
    `missing backend schema directory ${backendWorkspaceSchemaDir}`,
  ).toBeTruthy();

  // Compare the complete directory contract, not only the runtime type lookup map. Otherwise a new
  // backend schema (or the published-frame registry itself) could drift while this check continued
  // to report equality simply because the frontend had never registered the new filename.
  const bundledFiles = schemaFileNames(bundledSchemaDir);
  const backendFiles = schemaFileNames(backendWorkspaceSchemaDir);
  expect(bundledFiles, 'frontend/backend websocket schema filename sets must match').toEqual(
    backendFiles,
  );

  for (const file of backendFiles) {
    const bundledPath = path.join(bundledSchemaDir, file);
    const backendPath = path.join(backendWorkspaceSchemaDir, file);
    expect(JSON.parse(fs.readFileSync(bundledPath, 'utf-8')), `schema drift in ${file}`).toEqual(
      JSON.parse(fs.readFileSync(backendPath, 'utf-8')),
    );
  }
}

function schemaFileNames(dir: string): string[] {
  return fs
    .readdirSync(dir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.schema.json'))
    .map((entry) => entry.name)
    .sort();
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

  const dir = backendSchemaDir();
  expect(dir, 'backend websocket schema directory should exist').toBeTruthy();
  const publishedSchemaFiles = schemaFileNames(dir!).filter(
    (file) => file !== '_published-frame-registry.schema.json',
  );
  expect(
    publishedSchemaFiles,
    'every published websocket registry type must have exactly one matching schema file',
  ).toEqual(
    knownBackendSchemaTypes()
      .map((type) => `${type}.schema.json`)
      .sort(),
  );
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
