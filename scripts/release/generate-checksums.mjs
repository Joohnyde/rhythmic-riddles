#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { parseCliArgs, sha256File } from './release-lib.mjs';

const ALLOWED = [
  /\.deb$/i,
  /\.msi$/i,
  /\.dmg$/i,
  /^release-manifest-[a-z0-9_-]+\.json$/i,
  /^sbom-backend\.cdx\.json$/i,
  /^sbom-frontend\.cdx\.json$/i,
];

try {
  const args = parseCliArgs(process.argv.slice(2));
  const dir = path.resolve(args.dir ?? 'release-assets');
  const outPath = path.resolve(args.out ?? path.join(dir, 'SHA256SUMS'));

  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    throw new Error(`Release asset directory does not exist: ${dir}`);
  }

  const names = fs
    .readdirSync(dir)
    .filter((name) => ALLOWED.some((pattern) => pattern.test(name)))
    .sort((a, b) => a.localeCompare(b));

  if (names.length === 0) {
    throw new Error(`No intended release assets found in ${dir}.`);
  }

  const lines = names.map((name) => `${sha256File(path.join(dir, name))}  ${name}`);
  fs.writeFileSync(outPath, `${lines.join('\n')}\n`, 'utf8');

  console.log(`[release] wrote ${outPath}`);
  for (const line of lines) console.log(line);
} catch (error) {
  console.error(`[release] checksum generation failed: ${error.message}`);
  process.exit(1);
}
