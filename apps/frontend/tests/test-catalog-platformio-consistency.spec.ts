import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

type TestKey = { file: string; suite: string; testName: string };
type CatalogRow = Record<string, string>;

const FRONTEND_ROOT = process.cwd();
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '../..');
const FIRMWARE_ROOT = path.join(REPO_ROOT, 'hardware/firmware/receiver');
const TEST_ROOT = path.join(FIRMWARE_ROOT, 'test');
const TEST_CATALOG = path.join(REPO_ROOT, 'docs/developer-guide/testing/test-catalog.csv');

test('PlatformIO test catalog must match firmware Unity code', () => {
  const discovered = discoverPlatformIoTests();
  const catalog = parsePlatformIoCatalog();

  const discoveredSet = new Set(discovered.map(serialize));
  const catalogSet = new Set(catalog.entries.map(serialize));
  const missing = discovered.filter((entry) => !catalogSet.has(serialize(entry)));
  const stale = catalog.entries.filter((entry) => !discoveredSet.has(serialize(entry)));

  const errors: string[] = [];
  if (catalog.duplicates.length > 0) {
    errors.push(`Duplicate platformio entries:\n${format(catalog.duplicates)}`);
  }
  if (missing.length > 0) {
    errors.push(`PlatformIO tests missing from catalog:\n${format(missing)}`);
  }
  if (stale.length > 0) {
    errors.push(`Stale PlatformIO catalog entries:\n${format(stale)}`);
  }

  expect(errors.join('\n\n')).toBe('');
});

function discoverPlatformIoTests(): TestKey[] {
  if (!fs.existsSync(TEST_ROOT)) throw new Error(`PlatformIO test root not found: ${TEST_ROOT}`);

  return walk(TEST_ROOT)
    .filter((file) => file.endsWith('.cpp'))
    .flatMap((file) => {
      const source = fs.readFileSync(file, 'utf8');
      const catalogFile = normalizePath(path.relative(FIRMWARE_ROOT, file));
      const suiteDirectory = path.basename(path.dirname(file)).replace(/^test_/, '');
      const suite = suiteDirectory
        .split('_')
        .filter(Boolean)
        .map((part) => part[0].toUpperCase() + part.slice(1))
        .join('');

      return [...source.matchAll(/\bRUN_TEST\(\s*([A-Za-z0-9_]+)\s*\)/g)].map((match) => ({
        file: catalogFile,
        suite,
        testName: match[1],
      }));
    })
    .sort(compare);
}

function parsePlatformIoCatalog(): { entries: TestKey[]; duplicates: TestKey[] } {
  const rows = parseCsv(fs.readFileSync(TEST_CATALOG, 'utf8'));
  const entries: TestKey[] = [];
  const duplicates: TestKey[] = [];
  const seen = new Set<string>();

  for (const row of rows) {
    if ((row.framework ?? '').trim().toLowerCase() !== 'platformio') continue;

    const file = normalizePath((row.file ?? '').trim());
    const suite = (row.suite ?? '').trim();
    const testName = (row.test_name ?? '').trim();
    if (!file || !suite || !testName) {
      throw new Error('Malformed platformio row. Required: framework,file,suite,test_name');
    }
    if (path.isAbsolute(file) || file.startsWith('hardware/firmware/receiver/')) {
      throw new Error(
        `PlatformIO catalog paths must be relative to hardware/firmware/receiver: ${file}`,
      );
    }

    const key = { file, suite, testName };
    const serialized = serialize(key);
    if (seen.has(serialized)) duplicates.push(key);
    else {
      seen.add(serialized);
      entries.push(key);
    }
  }

  return { entries: entries.sort(compare), duplicates: duplicates.sort(compare) };
}

function walk(root: string): string[] {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(root, entry.name);
    return entry.isDirectory() ? walk(full) : [full];
  });
}

function parseCsv(content: string): CatalogRow[] {
  const lines = content
    .trim()
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0);
  const headers = splitCsvLine(lines[0]).map((header) => header.trim());
  return lines.slice(1).map((line) => {
    const values = splitCsvLine(line);
    return Object.fromEntries(headers.map((header, index) => [header, values[index] ?? '']));
  });
}

function splitCsvLine(line: string): string[] {
  const result: string[] = [];
  let value = '';
  let quoted = false;

  for (let index = 0; index < line.length; index++) {
    const char = line[index];
    if (char === '"' && line[index + 1] === '"') {
      value += '"';
      index++;
    } else if (char === '"') quoted = !quoted;
    else if (char === ',' && !quoted) {
      result.push(value);
      value = '';
    } else value += char;
  }
  result.push(value);
  return result;
}

function normalizePath(value: string): string {
  return value.replace(/\\/g, '/');
}

function serialize(key: TestKey): string {
  return `${key.file}|${key.suite}|${key.testName}`;
}

function compare(left: TestKey, right: TestKey): number {
  return serialize(left).localeCompare(serialize(right));
}

function format(keys: TestKey[]): string {
  return keys.map((key) => ` - ${serialize(key)}`).join('\n');
}
