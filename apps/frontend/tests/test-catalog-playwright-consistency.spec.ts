import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

type FrontendFramework = 'playwright' | 'vitest';

type TestKey = {
  file: string;
  suite: string;
  testName: string;
};

type CatalogRow = Record<string, string>;

type CatalogEntries = Record<FrontendFramework, TestKey[]>;
type CatalogDuplicates = Record<FrontendFramework, TestKey[]>;

const FRONTEND_ROOT = process.cwd();
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '../..');
const TEST_CATALOG = path.join(REPO_ROOT, 'docs/developer-guide/testing/test-catalog.csv');
const FRONTEND_FRAMEWORKS: readonly FrontendFramework[] = ['playwright', 'vitest'];

test('frontend test catalog must match Playwright and Vitest code', async () => {
  const catalog = parseCatalog(TEST_CATALOG);
  const discovered: CatalogEntries = {
    playwright: discoverPlaywrightTests(),
    vitest: discoverVitestTests(),
  };

  const errors: string[] = [];

  for (const framework of FRONTEND_FRAMEWORKS) {
    const missingInCatalog = difference(discovered[framework], catalog.entries[framework]);
    const staleInCatalog = difference(catalog.entries[framework], discovered[framework]);

    if (catalog.duplicates[framework].length > 0) {
      errors.push(
        `Duplicate ${framework} entries in test-catalog.csv:\n` +
          formatKeys(catalog.duplicates[framework]),
      );
    }

    if (missingInCatalog.length > 0) {
      errors.push(
        `${framework} tests present in code but missing in test-catalog.csv:\n` +
          formatKeys(missingInCatalog),
      );
    }

    if (staleInCatalog.length > 0) {
      errors.push(
        `${framework} entries present in test-catalog.csv but not found in code:\n` +
          formatKeys(staleInCatalog),
      );
    }
  }

  expect(errors.join('\n\n')).toBe('');
});

function discoverPlaywrightTests(): TestKey[] {
  return discoverTests(
    [path.join(FRONTEND_ROOT, 'e2e'), path.join(FRONTEND_ROOT, 'tests')],
    extractPlaywrightTestsFromFile,
  );
}

function discoverVitestTests(): TestKey[] {
  return discoverTests([path.join(FRONTEND_ROOT, 'src')], extractVitestTestsFromFile);
}

function discoverTests(
  roots: string[],
  extractor: (filePath: string, catalogFile: string) => TestKey[],
): TestKey[] {
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
    .flatMap((file) => extractor(path.join(FRONTEND_ROOT, file), file))
    .sort(compareKeys);
}

function extractPlaywrightTestsFromFile(filePath: string, catalogFile: string): TestKey[] {
  const source = fs.readFileSync(filePath, 'utf8');
  const suites = extractDescribeRanges(
    source,
    /test\.describe(?:\.(?:serial|parallel|only|skip))?\(\s*['"`]([^'"`]+)['"`]/g,
  );
  const tests = extractNamedCalls(
    source,
    /(?<!\.)\btest(?:\.(?:only|skip|fixme|slow))?\(\s*['"`]([^'"`]+)['"`]/g,
  );

  return toTestKeys(catalogFile, suites, tests);
}

function extractVitestTestsFromFile(filePath: string, catalogFile: string): TestKey[] {
  const source = fs.readFileSync(filePath, 'utf8');
  const suites = extractDescribeRanges(
    source,
    /(?<!\.)\bdescribe(?:\.(?:only|skip|concurrent|shuffle))?\(\s*['"`]([^'"`]+)['"`]/g,
  );
  const tests = extractNamedCalls(
    source,
    /(?<!\.)\b(?:it|test)(?:\.(?:only|skip|todo|fails|concurrent))?\(\s*['"`]([^'"`]+)['"`]/g,
  );

  return toTestKeys(catalogFile, suites, tests);
}

function toTestKeys(
  catalogFile: string,
  suites: Array<{ name: string; start: number; end: number }>,
  tests: Array<{ name: string; index: number }>,
): TestKey[] {
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
  describeRegex: RegExp,
): Array<{ name: string; start: number; end: number }> {
  const ranges: Array<{ name: string; start: number; end: number }> = [];

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

function extractNamedCalls(
  source: string,
  callRegex: RegExp,
): Array<{ name: string; index: number }> {
  return [...source.matchAll(callRegex)].map((match) => ({
    name: match[1],
    index: match.index ?? 0,
  }));
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

function parseCatalog(catalogPath: string): {
  entries: CatalogEntries;
  duplicates: CatalogDuplicates;
} {
  if (!fs.existsSync(catalogPath)) {
    throw new Error(`Test catalog not found: ${catalogPath}`);
  }

  const rows = parseCsv(fs.readFileSync(catalogPath, 'utf8'));
  const entries: CatalogEntries = { playwright: [], vitest: [] };
  const duplicates: CatalogDuplicates = { playwright: [], vitest: [] };
  const seen: Record<FrontendFramework, Set<string>> = {
    playwright: new Set<string>(),
    vitest: new Set<string>(),
  };

  for (const row of rows) {
    const framework = normalize(row.framework);
    if (!isFrontendFramework(framework)) {
      continue;
    }

    const file = normalizePath((row.file ?? '').trim());
    const suite = (row.suite ?? '').trim() || path.basename(file);
    const testName = (row.test_name ?? '').trim();

    if (!file || !testName) {
      throw new Error(
        `Malformed ${framework} row in test-catalog.csv. Required: framework,file,test_name`,
      );
    }

    if (!file.endsWith('.spec.ts')) {
      throw new Error(`Unsupported ${framework} catalog file extension: ${file}`);
    }

    const key = { file, suite, testName };
    const serialized = serializeKey(key);

    if (seen[framework].has(serialized)) {
      duplicates[framework].push(key);
    } else {
      seen[framework].add(serialized);
      entries[framework].push(key);
    }
  }

  for (const framework of FRONTEND_FRAMEWORKS) {
    entries[framework].sort(compareKeys);
    duplicates[framework].sort(compareKeys);
  }

  return { entries, duplicates };
}

function isFrontendFramework(value: string): value is FrontendFramework {
  return FRONTEND_FRAMEWORKS.includes(value as FrontendFramework);
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

function formatKeys(keys: TestKey[]): string {
  return keys
    .map(formatKey)
    .map((value) => ` - ${value}`)
    .join('\n');
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
