import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildCodeFromEpochSeconds,
  officialArtifactName,
  parseReleaseTag,
  validateConfig,
} from './release-lib.mjs';

const config = {
  appName: 'cestereg',
  releaseVersion: '0.2.0',
  sourceVersion: '0.3.0',
  releaseBranch: 'master',
  platforms: {
    linux: { arch: 'x64', artifactExtension: 'deb' },
    windows: { arch: 'x64', artifactExtension: 'msi' },
    macos: { arch: 'x64', artifactExtension: 'dmg' },
  },
};

test('accepts final and supported prerelease tags', () => {
  assert.deepEqual(parseReleaseTag('v0.2.0'), {
    tag: 'v0.2.0',
    version: '0.2.0',
    channel: null,
    sequence: null,
    prerelease: false,
  });

  assert.deepEqual(parseReleaseTag('v0.2.0-rc.1'), {
    tag: 'v0.2.0-rc.1',
    version: '0.2.0',
    channel: 'rc',
    sequence: 1,
    prerelease: true,
  });

  assert.equal(parseReleaseTag('v1.0.0-alpha.2').channel, 'alpha');
  assert.equal(parseReleaseTag('v1.0.0-beta.3').channel, 'beta');
});

test('rejects malformed or unsupported tags', () => {
  for (const tag of [
    '0.2.0',
    'v0.2',
    'v00.2.0',
    'v0.02.0',
    'v0.2.00',
    'v0.2.0-rc',
    'v0.2.0-rc.0',
    'v0.2.0-RC.1',
    'v0.2.0-preview.1',
    'v0.2.0+build.1',
  ]) {
    assert.throws(() => parseReleaseTag(tag), /Invalid release tag/);
  }
});

test('generates deterministic six-character Crockford Base32 build codes', () => {
  assert.equal(buildCodeFromEpochSeconds(0), '000000');
  assert.equal(buildCodeFromEpochSeconds(1), '000001');
  assert.equal(buildCodeFromEpochSeconds(1757334896), '4BXKBG');
  assert.equal(buildCodeFromEpochSeconds(1790931600), '5BYWMG');
  assert.equal(buildCodeFromEpochSeconds(0xffffffff), 'FZZZZZ');
});

test('rejects timestamps outside unsigned 32-bit range', () => {
  assert.throws(() => buildCodeFromEpochSeconds(-1), /unsigned 32-bit/);
  assert.throws(() => buildCodeFromEpochSeconds(0x100000000), /unsigned 32-bit/);
  assert.throws(() => buildCodeFromEpochSeconds(1.5), /unsigned 32-bit/);
});

test('builds official artifact names from release identity, not source version', () => {
  assert.equal(
    officialArtifactName(config, 'linux', '05J7M4'),
    'cestereg-0.2.0-05J7M4-linux-x64.deb',
  );
  assert.equal(
    officialArtifactName(config, 'windows', '05J7M4'),
    'cestereg-0.2.0-05J7M4-windows-x64.msi',
  );
  assert.equal(
    officialArtifactName(config, 'macos', '05J7M4'),
    'cestereg-0.2.0-05J7M4-macos-x64.dmg',
  );
});

test('validates the Foundation release configuration', () => {
  assert.doesNotThrow(() => validateConfig(config));
  assert.throws(
    () => validateConfig({ ...config, releaseVersion: 'v0.2.0' }),
    /releaseVersion/,
  );
  assert.throws(
    () => validateConfig({ ...config, platforms: {} }),
    /At least one release platform/,
  );
});
