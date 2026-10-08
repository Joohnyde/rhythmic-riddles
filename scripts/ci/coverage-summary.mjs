#!/usr/bin/env node
import { readFile, readdir, stat } from 'node:fs/promises';
import path from 'node:path';

const args = parseArgs(process.argv.slice(2));
const sections = [];

if (args.backend) sections.push(await backendSummary(args.backend));
if (args.frontend) sections.push(await frontendSummary(args.frontend));
if (sections.length === 0) {
  throw new Error('Usage: coverage-summary.mjs [--backend <jacoco.csv>] [--frontend <coverage-summary.json>]');
}

process.stdout.write(`${sections.join('\n\n')}\n`);

async function backendSummary(file) {
  try {
    const rows = csv(await readFile(file, 'utf8'));
    const header = rows.shift();
    const index = Object.fromEntries(header.map((name, i) => [name, i]));
    const metrics = {
      Instructions: ['INSTRUCTION_MISSED', 'INSTRUCTION_COVERED'],
      Branches: ['BRANCH_MISSED', 'BRANCH_COVERED'],
      Lines: ['LINE_MISSED', 'LINE_COVERED'],
      Methods: ['METHOD_MISSED', 'METHOD_COVERED'],
      Classes: ['CLASS_MISSED', 'CLASS_COVERED'],
    };
    const totals = Object.fromEntries(Object.keys(metrics).map((name) => [name, [0, 0]]));
    for (const row of rows) {
      for (const [name, [missedKey, coveredKey]] of Object.entries(metrics)) {
        totals[name][0] += Number(row[index[missedKey]] ?? 0);
        totals[name][1] += Number(row[index[coveredKey]] ?? 0);
      }
    }
    return markdown('Backend coverage (JaCoCo)', totals);
  } catch (error) {
    return `### Backend coverage (JaCoCo)\n\nReport unavailable: ${message(error)}`;
  }
}

async function frontendSummary(file) {
  try {
    const summaryFile = await resolveCoverageSummary(file);
    const json = JSON.parse(await readFile(summaryFile, 'utf8'));
    const total = json.total ?? {};
    const rows = {};
    for (const [label, key] of [
      ['Statements', 'statements'],
      ['Branches', 'branches'],
      ['Functions', 'functions'],
      ['Lines', 'lines'],
    ]) {
      const metric = total[key];
      if (!metric) continue;
      rows[label] = [Number(metric.total) - Number(metric.covered), Number(metric.covered)];
    }
    return markdown('Frontend coverage (Vitest V8)', rows);
  } catch (error) {
    return `### Frontend coverage (Vitest V8)\n\nReport unavailable: ${message(error)}`;
  }
}


async function resolveCoverageSummary(candidate) {
  const info = await stat(candidate);
  if (info.isFile()) return candidate;
  const matches = [];
  await walk(candidate, matches);
  if (matches.length !== 1) {
    throw new Error(`Expected one coverage-summary.json below ${candidate}; found ${matches.length}`);
  }
  return matches[0];
}

async function walk(directory, matches) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) await walk(full, matches);
    else if (entry.name === 'coverage-summary.json') matches.push(full);
  }
}

function markdown(title, totals) {
  const lines = [
    `### ${title}`,
    '',
    '| Metric | Covered | Total | Coverage |',
    '|---|---:|---:|---:|',
  ];
  for (const [name, [missed, covered]] of Object.entries(totals)) {
    const total = missed + covered;
    const pct = total === 0 ? 'n/a' : `${((covered / total) * 100).toFixed(2)}%`;
    lines.push(`| ${name} | ${covered} | ${total} | ${pct} |`);
  }
  lines.push('', '> Baseline only: no percentage threshold is enforced for Foundation 0.2.0.');
  return lines.join('\n');
}

function csv(text) {
  return text.trim().split(/\r?\n/).filter(Boolean).map((line) => line.split(','));
}

function parseArgs(values) {
  const result = {};
  for (let i = 0; i < values.length; i += 2) {
    const key = values[i];
    const value = values[i + 1];
    if (!key?.startsWith('--') || value === undefined) throw new Error(`Invalid argument near ${key ?? '<end>'}`);
    result[key.slice(2)] = value;
  }
  return result;
}

function message(error) {
  return error instanceof Error ? error.message : String(error);
}
