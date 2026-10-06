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
} from './release-lib.mjs';

try {
  const args = parseCliArgs(process.argv.slice(2));
  const config = loadReleaseConfig();
  const tag = resolveReleaseTag(args.tag);
  const parsedTag = parseReleaseTag(tag);
  const sha = resolveGitSha(args.sha);
  const dir = path.resolve(args.dir ?? 'release-assets');

  if (parsedTag.version !== config.releaseVersion) {
    throw new Error(
      `Tag ${tag} targets ${parsedTag.version}, expected ${config.releaseVersion}.`,
    );
  }

  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    throw new Error(`Release asset directory does not exist: ${dir}`);
  }

  const buildCode = buildCodeFromEpochSeconds(commitEpochSeconds(sha));
  const expected = [];

  for (const platform of Object.keys(config.platforms)) {
    expected.push(officialArtifactName(config, platform, buildCode));
    expected.push(manifestName(config, platform));
  }

  expected.push('sbom-backend.cdx.json');
  expected.push('sbom-frontend.cdx.json');
  expected.push('SHA256SUMS');
  expected.sort((a, b) => a.localeCompare(b));

  const actual = fs
    .readdirSync(dir)
    .filter((name) => fs.statSync(path.join(dir, name)).isFile())
    .sort((a, b) => a.localeCompare(b));

  const missing = expected.filter((name) => !actual.includes(name));
  const unexpected = actual.filter((name) => !expected.includes(name));

  if (missing.length > 0) throw new Error(`Missing release assets: ${missing.join(', ')}`);
  if (unexpected.length > 0) {
    throw new Error(`Unexpected files in release asset directory: ${unexpected.join(', ')}`);
  }

  verifyChecksumFile(dir, expected.filter((name) => name !== 'SHA256SUMS'));
  verifyManifests(dir, config, tag, parsedTag.prerelease, sha, buildCode);

  console.log(`[release] asset set valid (${expected.length} files)`);
} catch (error) {
  console.error(`[release] asset validation failed: ${error.message}`);
  process.exit(1);
}

function verifyChecksumFile(dir, expectedHashedFiles) {
  const checksumPath = path.join(dir, 'SHA256SUMS');
  const lines = fs
    .readFileSync(checksumPath, 'utf8')
    .split(/\r?\n/)
    .filter(Boolean);

  const entries = new Map();
  for (const line of lines) {
    const match = /^([0-9a-f]{64})  (.+)$/i.exec(line);
    if (!match) throw new Error(`Malformed SHA256SUMS line: ${line}`);
    entries.set(match[2], match[1].toLowerCase());
  }

  const names = [...entries.keys()].sort((a, b) => a.localeCompare(b));
  const expected = [...expectedHashedFiles].sort((a, b) => a.localeCompare(b));

  if (JSON.stringify(names) !== JSON.stringify(expected)) {
    throw new Error('SHA256SUMS does not contain exactly the expected release assets.');
  }

  for (const [name, expectedHash] of entries) {
    const actualHash = sha256File(path.join(dir, name));
    if (actualHash !== expectedHash) {
      throw new Error(`Checksum mismatch for ${name}.`);
    }
  }
}

function verifyManifests(dir, config, tag, prerelease, sha, buildCode) {
  for (const [platform, platformConfig] of Object.entries(config.platforms)) {
    const file = manifestName(config, platform);
    const manifest = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
    const expectedArtifact = officialArtifactName(config, platform, buildCode);

    if (manifest.releaseTag !== tag) {
      throw new Error(`${file}: wrong release tag.`);
    }
    if (manifest.version !== config.releaseVersion) {
      throw new Error(`${file}: wrong release version.`);
    }
    if (manifest.prerelease !== prerelease) {
      throw new Error(`${file}: wrong prerelease state.`);
    }
    if (manifest.sourceVersion !== config.sourceVersion) {
      throw new Error(`${file}: wrong source version.`);
    }
    if (manifest.gitCommit !== sha) {
      throw new Error(`${file}: wrong Git commit.`);
    }
    if (manifest.buildCode !== buildCode) {
      throw new Error(`${file}: wrong build code.`);
    }
    if (manifest.releaseBranch !== config.releaseBranch) {
      throw new Error(`${file}: wrong release branch.`);
    }
    if (manifest.platform !== platform || manifest.arch !== platformConfig.arch) {
      throw new Error(`${file}: wrong platform or architecture.`);
    }
    if (manifest.artifacts?.length !== 1 || manifest.artifacts[0].name !== expectedArtifact) {
      throw new Error(`${file}: wrong artifact list.`);
    }

    const actualHash = sha256File(path.join(dir, expectedArtifact));
    if (manifest.artifacts[0].sha256 !== actualHash) {
      throw new Error(`${file}: artifact checksum does not match.`);
    }
  }
}
