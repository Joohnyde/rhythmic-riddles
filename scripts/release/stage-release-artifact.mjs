#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import {
  buildCodeFromEpochSeconds,
  commitEpochSeconds,
  loadReleaseConfig,
  manifestName,
  officialArtifactName,
  parseCliArgs,
  parseReleaseTag,
  resolveGitSha,
  resolveReleaseTag,
  sha256File,
  writeGithubOutputs,
} from './release-lib.mjs';

try {
  const args = parseCliArgs(process.argv.slice(2));
  if (!args.platform) throw new Error('--platform is required.');
  if (!args.input) throw new Error('--input is required.');

  const config = loadReleaseConfig();
  const tag = resolveReleaseTag(args.tag);
  const sha = resolveGitSha(args.sha);
  const parsedTag = parseReleaseTag(tag);
  const platformConfig = config.platforms[args.platform];

  if (!platformConfig) throw new Error(`Unsupported release platform: ${args.platform}`);
  if (parsedTag.version !== config.releaseVersion) {
    throw new Error(
      `Tag ${tag} targets ${parsedTag.version}, expected ${config.releaseVersion}.`,
    );
  }

  const input = path.resolve(args.input);
  if (!fs.statSync(input).isFile()) throw new Error(`Artifact is not a file: ${input}`);

  const expectedInputExtension = `.${platformConfig.artifactExtension}`.toLowerCase();
  if (path.extname(input).toLowerCase() !== expectedInputExtension) {
    throw new Error(
      `Expected a ${expectedInputExtension} artifact for ${args.platform}, got ${input}.`,
    );
  }

  const outDir = path.resolve(args['out-dir'] ?? 'release-assets');
  fs.mkdirSync(outDir, { recursive: true });

  const epochSeconds = commitEpochSeconds(sha);
  const buildCode = buildCodeFromEpochSeconds(epochSeconds);
  const artifactName = officialArtifactName(config, args.platform, buildCode);
  const artifactPath = path.join(outDir, artifactName);

  fs.copyFileSync(input, artifactPath);

  const manifest = {
    releaseTag: tag,
    version: config.releaseVersion,
    sourceVersion: config.sourceVersion,
    buildCode,
    gitCommit: sha,
    commitTime: new Date(epochSeconds * 1000).toISOString(),
    releaseBranch: config.releaseBranch,
    prerelease: parsedTag.prerelease,
    platform: args.platform,
    arch: platformConfig.arch,
    artifacts: [
      {
        name: artifactName,
        sha256: sha256File(artifactPath),
      },
    ],
  };

  const manifestPath = path.join(outDir, manifestName(config, args.platform));
  fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

  console.log(`[release] staged artifact: ${artifactPath}`);
  console.log(`[release] wrote manifest:  ${manifestPath}`);

  writeGithubOutputs({
    artifact_path: artifactPath,
    artifact_name: artifactName,
    manifest_path: manifestPath,
    manifest_name: path.basename(manifestPath),
    build_code: buildCode,
  });
} catch (error) {
  console.error(`[release] staging failed: ${error.message}`);
  process.exit(1);
}
