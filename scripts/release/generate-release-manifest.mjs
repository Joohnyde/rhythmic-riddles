#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import {
  createReleaseManifest,
  loadReleaseConfig,
  manifestName,
  parseCliArgs,
  resolveGitSha,
  resolveReleaseTag,
  writeGithubOutputs,
} from './release-lib.mjs';

try {
  const args = parseCliArgs(process.argv.slice(2));
  if (!args.platform) throw new Error('--platform is required.');
  if (!args.artifact) throw new Error('--artifact is required.');

  const config = loadReleaseConfig();
  const tag = resolveReleaseTag(args.tag);
  const sha = resolveGitSha(args.sha);
  const artifactPath = path.resolve(args.artifact);

  if (!fs.existsSync(artifactPath) || !fs.statSync(artifactPath).isFile()) {
    throw new Error(`Artifact does not exist: ${artifactPath}`);
  }

  const manifest = createReleaseManifest({
    config,
    tag,
    sha,
    platform: args.platform,
    artifactPath,
  });

  const outPath = path.resolve(
    args.out ?? path.join(path.dirname(artifactPath), manifestName(config, args.platform)),
  );

  fs.writeFileSync(outPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  console.log(`[release] wrote manifest: ${outPath}`);

  writeGithubOutputs({
    manifest_path: outPath,
    manifest_name: path.basename(outPath),
    build_code: manifest.buildCode,
  });
} catch (error) {
  console.error(`[release] manifest generation failed: ${error.message}`);
  process.exit(1);
}
