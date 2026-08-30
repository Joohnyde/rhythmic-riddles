import { expect, test } from '@playwright/test';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

type CatalogRow = Record<string, string>;

const FRONTEND_ROOT = process.cwd();
const REPO_ROOT = path.resolve(FRONTEND_ROOT, '../..');
const TEST_CATALOG = path.join(REPO_ROOT, 'docs/developer-guide/testing/test-catalog.csv');

test('standalone Node harness catalog entries must reference valid scripts', () => {
  const rows = parseCsv(fs.readFileSync(TEST_CATALOG, 'utf8')).filter(
    (row) => (row.framework ?? '').trim().toLowerCase() === 'node',
  );
  const errors: string[] = [];
  const seen = new Set<string>();

  if (rows.length === 0) errors.push('No standalone Node harness is catalogued.');

  for (const row of rows) {
    const file = normalizePath((row.file ?? '').trim());
    const suite = (row.suite ?? '').trim();
    const testName = (row.test_name ?? '').trim();
    const key = `${file}|${suite}|${testName}`;

    if (!file || !suite || !testName) {
      errors.push('Malformed node row. Required: file,suite,test_name.');
      continue;
    }
    if (path.isAbsolute(file) || file.startsWith('../')) {
      errors.push(`Node harness paths must be repository-relative: ${file}`);
      continue;
    }
    if (seen.has(key)) errors.push(`Duplicate node catalog entry: ${key}`);
    seen.add(key);

    const absolute = path.join(REPO_ROOT, file);
    if (!fs.existsSync(absolute)) {
      errors.push(`Node harness does not exist: ${file}`);
      continue;
    }
    if (!file.endsWith('.mjs')) {
      errors.push(`Standalone Node harness must use .mjs: ${file}`);
      continue;
    }

    const syntax = spawnSync(process.execPath, ['--check', absolute], { encoding: 'utf8' });
    if (syntax.status !== 0) {
      errors.push(`Node harness syntax check failed for ${file}: ${syntax.stderr.trim()}`);
    }
  }

  expect(errors.join('\n')).toBe('');
});

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
