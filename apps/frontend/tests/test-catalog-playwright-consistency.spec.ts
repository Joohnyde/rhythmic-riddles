import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

type TestKey = {
  file: string;
  suite: string;
  testName: string;
};

type CatalogRow = Record<string, string>;

const FRONTEND_ROOT = process.cwd();
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '../..');
const TEST_CATALOG = path.join(REPO_ROOT, 'docs/developer-guide/testing/test-catalog.csv');

test('playwright test catalog must match code', async () => {
  const catalog = parseCatalog(TEST_CATALOG);
  const discovered = discoverPlaywrightTests(catalog.entries.map((entry) => entry.file));

  const missingInCatalog = difference(discovered, catalog.entries);
  const staleInCatalog = difference(catalog.entries, discovered);

  const errors: string[] = [];

  if (catalog.duplicates.length > 0) {
    errors.push(
      'Duplicate playwright entries in test-catalog.csv:\n' +
        catalog.duplicates
          .map(formatKey)
          .map((value) => ` - ${value}`)
          .join('\n'),
    );
  }

  if (missingInCatalog.length > 0) {
    errors.push(
      'Playwright tests present in code but missing in test-catalog.csv:\n' +
        missingInCatalog
          .map(formatKey)
          .map((value) => ` - ${value}`)
          .join('\n'),
    );
  }

  if (staleInCatalog.length > 0) {
    errors.push(
      'Playwright entries present in test-catalog.csv but not found in code:\n' +
        staleInCatalog
          .map(formatKey)
          .map((value) => ` - ${value}`)
          .join('\n'),
    );
  }

  expect(errors.join('\n\n')).toBe('');
});

function discoverPlaywrightTests(catalogFiles: string[]): TestKey[] {
  const roots = candidateRoots(catalogFiles);
  const testFiles = new Set<string>();

  for (const root of roots) {
    if (!fs.existsSync(root)) {
      continue;
    }

    for (const file of walk(root)) {
      if (file.endsWith('.spec.ts')) {
        testFiles.add(normalizePath(path.relative(FRONTEND_ROOT, file)));
      }
    }
  }

  return [...testFiles]
    .flatMap((file) => extractTestsFromFile(path.join(FRONTEND_ROOT, file), file))
    .sort(compareKeys);
}

function candidateRoots(catalogFiles: string[]): string[] {
  const directories = new Set<string>();

  for (const file of catalogFiles) {
    const normalized = normalizePath(file);
    const directory = path.dirname(normalized);

    if (directory && directory !== '.') {
      directories.add(path.join(FRONTEND_ROOT, directory));
    }
  }

  directories.add(path.join(FRONTEND_ROOT, 'e2e'));
  directories.add(path.join(FRONTEND_ROOT, 'tests'));
  directories.add(path.join(FRONTEND_ROOT, 'src'));

  return [...directories];
}

function extractTestsFromFile(filePath: string, catalogFile: string): TestKey[] {
  const source = fs.readFileSync(filePath, 'utf8');
  const suites = extractDescribeRanges(source);
  const tests = extractTestCalls(source);

  return tests.map((foundTest) => {
    const suite =
      [...suites]
        .reverse()
        .find((candidate) => candidate.start < foundTest.index && foundTest.index < candidate.end)
        ?.name ?? path.basename(catalogFile);

    return {
      file: normalizePath(catalogFile),
      suite,
      testName: foundTest.name,
    };
  });
}

function extractDescribeRanges(
  source: string,
): Array<{ name: string; start: number; end: number }> {
  const ranges: Array<{ name: string; start: number; end: number }> = [];
  const describeRegex =
    /test\.describe(?:\.(?:serial|parallel|only|skip))?\(\s*['"`]([^'"`]+)['"`]/g;

  for (const match of source.matchAll(describeRegex)) {
    const start = match.index ?? 0;
    ranges.push({
      name: match[1],
      start,
      end: findBlockEnd(source, start),
    });
  }

  return ranges;
}

function extractTestCalls(source: string): Array<{ name: string; index: number }> {
  const tests: Array<{ name: string; index: number }> = [];
  const testRegex = /(?<!\.)\btest(?:\.(?:only|skip|fixme|slow))?\(\s*['"`]([^'"`]+)['"`]/g;

  for (const match of source.matchAll(testRegex)) {
    tests.push({
      name: match[1],
      index: match.index ?? 0,
    });
  }

  return tests;
}

function findBlockEnd(source: string, start: number): number {
  const openBrace = source.indexOf('{', start);
  if (openBrace < 0) {
    return source.length;
  }

  let depth = 0;
  for (let i = openBrace; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}') depth--;

    if (depth === 0) {
      return i;
    }
  }

  return source.length;
}

function parseCatalog(catalogPath: string): { entries: TestKey[]; duplicates: TestKey[] } {
  if (!fs.existsSync(catalogPath)) {
    throw new Error(`Test catalog not found: ${catalogPath}`);
  }

  const rows = parseCsv(fs.readFileSync(catalogPath, 'utf8'));
  const entries: TestKey[] = [];
  const duplicates: TestKey[] = [];
  const seen = new Set<string>();

  for (const row of rows) {
    if (normalize(row.framework) !== 'playwright') {
      continue;
    }

    const file = normalizePath((row.file ?? '').trim());
    const suite = (row.suite ?? '').trim() || path.basename(file);
    const testName = (row.test_name ?? '').trim();

    if (!file || !testName) {
      throw new Error(
        'Malformed playwright row in test-catalog.csv. Required: framework,file,test_name',
      );
    }

    if (!file.endsWith('.spec.ts')) {
      throw new Error(`Unsupported playwright catalog file extension: ${file}`);
    }

    const key = { file, suite, testName };
    const serialized = serializeKey(key);

    if (seen.has(serialized)) {
      duplicates.push(key);
    } else {
      seen.add(serialized);
      entries.push(key);
    }
  }

  return {
    entries: entries.sort(compareKeys),
    duplicates: duplicates.sort(compareKeys),
  };
}

function walk(root: string): string[] {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(root, entry.name);
    return entry.isDirectory() ? walk(fullPath) : [fullPath];
  });
}

function difference(left: TestKey[], right: TestKey[]): TestKey[] {
  const rightSet = new Set(right.map(serializeKey));
  return left.filter((item) => !rightSet.has(serializeKey(item))).sort(compareKeys);
}

function serializeKey(key: TestKey): string {
  return `${key.file}|${key.suite}|${key.testName}`;
}

function formatKey(key: TestKey): string {
  return `${key.file} | ${key.suite} | ${key.testName}`;
}

function compareKeys(a: TestKey, b: TestKey): number {
  return serializeKey(a).localeCompare(serializeKey(b));
}

function normalizePath(value: string): string {
  return value.replace(/\\/g, '/');
}

function normalize(value: string | undefined): string {
  return (value ?? '').trim().toLowerCase();
}

function parseCsv(content: string): CatalogRow[] {
  const lines = content
    .trim()
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0);
  const headers = splitCsvLine(lines[0]).map((header) => header.trim());

  for (const required of ['framework', 'file', 'suite', 'test_name']) {
    if (!headers.includes(required)) {
      throw new Error(`Missing required test catalog header: ${required}`);
    }
  }

  return lines.slice(1).map((line) => {
    const values = splitCsvLine(line);
    return Object.fromEntries(headers.map((header, index) => [header, values[index] ?? '']));
  });
}

function splitCsvLine(line: string): string[] {
  const result: string[] = [];
  let current = '';
  let quoted = false;

  for (let i = 0; i < line.length; i++) {
    const char = line[i];

    if (char === '"' && line[i + 1] === '"') {
      current += '"';
      i++;
    } else if (char === '"') {
      quoted = !quoted;
    } else if (char === ',' && !quoted) {
      result.push(current);
      current = '';
    } else {
      current += char;
    }
  }

  result.push(current);
  return result;
}
