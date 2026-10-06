import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const RELEASE_TAG_RE =
  /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(rc|alpha|beta)\.([1-9]\d*))?$/;

const CROCKFORD_BASE32 = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
export const repoRoot = path.resolve(scriptDir, '../..');
export const configPath = path.join(scriptDir, 'release-config.json');

export function loadReleaseConfig() {
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  validateConfig(config);
  return config;
}

export function validateConfig(config) {
  if (!config || typeof config !== 'object') throw new Error('Release config must be an object.');
  if (!/^[a-z0-9][a-z0-9._-]*$/i.test(config.appName ?? '')) {
    throw new Error('release-config.json has an invalid appName.');
  }
  if (!isStableSemVer(config.releaseVersion)) {
    throw new Error('releaseVersion must be MAJOR.MINOR.PATCH without a leading v.');
  }
  if (!isStableSemVer(config.sourceVersion)) {
    throw new Error('sourceVersion must be MAJOR.MINOR.PATCH without a leading v.');
  }
  if (!/^[A-Za-z0-9._/-]+$/.test(config.releaseBranch ?? '')) {
    throw new Error('releaseBranch is missing or invalid.');
  }
  if (!config.platforms || Object.keys(config.platforms).length === 0) {
    throw new Error('At least one release platform must be configured.');
  }

  for (const [platform, entry] of Object.entries(config.platforms)) {
    if (!entry?.arch || !entry?.artifactExtension) {
      throw new Error(`Platform ${platform} must define arch and artifactExtension.`);
    }
  }
}

export function parseReleaseTag(tag) {
  const match = RELEASE_TAG_RE.exec(tag ?? '');
  if (!match) {
    throw new Error(
      `Invalid release tag "${tag ?? ''}". Expected vMAJOR.MINOR.PATCH or ` +
        'vMAJOR.MINOR.PATCH-(rc|alpha|beta).N.',
    );
  }

  const [, major, minor, patch, channel, sequence] = match;
  const version = `${major}.${minor}.${patch}`;

  return {
    tag,
    version,
    channel: channel ?? null,
    sequence: sequence ? Number(sequence) : null,
    prerelease: Boolean(channel),
  };
}

export function isStableSemVer(value) {
  try {
    const parsed = parseReleaseTag(`v${value}`);
    return !parsed.prerelease;
  } catch {
    return false;
  }
}

export function buildCodeFromEpochSeconds(epochSeconds) {
  if (!Number.isInteger(epochSeconds) || epochSeconds < 0 || epochSeconds > 0xffffffff) {
    throw new Error('Commit timestamp must be an unsigned 32-bit Unix timestamp.');
  }

  let value = BigInt(epochSeconds) & 0x1fffffffn;
  let encoded = '';

  do {
    encoded = CROCKFORD_BASE32[Number(value % 32n)] + encoded;
    value /= 32n;
  } while (value > 0n);

  return encoded.padStart(6, '0');
}

export function officialArtifactName(config, platform, buildCode) {
  const platformConfig = config.platforms[platform];
  if (!platformConfig) throw new Error(`Unsupported release platform: ${platform}`);

  return [
    config.appName,
    config.releaseVersion,
    buildCode,
    platform,
    platformConfig.arch,
  ].join('-') + `.${platformConfig.artifactExtension}`;
}

export function manifestName(config, platform) {
  const platformConfig = config.platforms[platform];
  if (!platformConfig) throw new Error(`Unsupported release platform: ${platform}`);
  return `release-manifest-${platform}-${platformConfig.arch}.json`;
}

export function runGit(args, options = {}) {
  const output = execFileSync('git', args, {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    ...options,
  });
  return typeof output === 'string' ? output.trim() : '';
}

export function resolveGitSha(explicitSha) {
  const sha = explicitSha || process.env.GITHUB_SHA || runGit(['rev-parse', 'HEAD']);
  if (!/^[0-9a-f]{40}$/i.test(sha)) {
    throw new Error(`Expected a full 40-character Git SHA, got "${sha}".`);
  }
  return sha.toLowerCase();
}

export function resolveReleaseTag(explicitTag) {
  const tag = explicitTag || process.env.GITHUB_REF_NAME;
  if (!tag) {
    throw new Error('Release tag is required. Pass --tag or set GITHUB_REF_NAME.');
  }
  return tag;
}

export function commitEpochSeconds(sha) {
  const raw = runGit(['show', '-s', '--format=%ct', sha]);
  const value = Number(raw);
  if (!Number.isInteger(value)) {
    throw new Error(`Could not read commit timestamp for ${sha}.`);
  }
  return value;
}

export function commitIsoTime(sha) {
  return new Date(commitEpochSeconds(sha) * 1000).toISOString();
}

export function assertCommitOnReleaseBranch(sha, releaseBranch) {
  const remoteRef = `refs/remotes/origin/${releaseBranch}`;

  try {
    runGit(['show-ref', '--verify', '--quiet', remoteRef], { stdio: 'ignore' });
  } catch {
    throw new Error(
      `Missing ${remoteRef}. Fetch the release branch before validation, for example: ` +
        `git fetch --no-tags origin +refs/heads/${releaseBranch}:refs/remotes/origin/${releaseBranch}`,
    );
  }

  try {
    runGit(['merge-base', '--is-ancestor', sha, remoteRef], { stdio: 'ignore' });
  } catch {
    throw new Error(
      `Release commit ${sha} is not contained in origin/${releaseBranch}.`,
    );
  }
}

export function readSourceVersions() {
  const frontendPackagePath = path.join(repoRoot, 'apps/frontend/package.json');
  const frontendLockPath = path.join(repoRoot, 'apps/frontend/package-lock.json');
  const pomPath = path.join(repoRoot, 'apps/backend/pom.xml');

  const frontend = JSON.parse(fs.readFileSync(frontendPackagePath, 'utf8'));
  const lockfile = JSON.parse(fs.readFileSync(frontendLockPath, 'utf8'));
  const pom = fs.readFileSync(pomPath, 'utf8');

  const pomMatch = pom.match(
    /<artifactId>cestereg<\/artifactId>\s*<version>([^<]+)<\/version>/,
  );
  if (!pomMatch) {
    throw new Error('Missing backend project version in apps/backend/pom.xml.');
  }

  return {
    backend: pomMatch[1].trim(),
    frontend: frontend.version,
    lockfile: lockfile.packages?.['']?.version,
    builders: {
      linux: readShellBuilderVersion('scripts/prod/build/build_linux_jpackage.sh'),
      windows: readPowerShellBuilderVersion('scripts/prod/build/build_windows_jpackage.ps1'),
      macos: readShellBuilderVersion('scripts/prod/build/build_macos_jpackage.sh'),
    },
  };
}

export function assertSourceVersionContract(config) {
  const versions = readSourceVersions();
  const values = [
    ['backend', versions.backend],
    ['frontend', versions.frontend],
    ['lockfile', versions.lockfile],
    ['linux builder', versions.builders.linux],
    ['windows builder', versions.builders.windows],
    ['macOS builder', versions.builders.macos],
  ];

  for (const [label, value] of values) {
    if (value !== config.sourceVersion) {
      throw new Error(
        `${label} version is ${value ?? '<missing>'}, expected sourceVersion ${config.sourceVersion}.`,
      );
    }
  }

  return versions;
}

export function assertFinalChangelog(version) {
  const changelogPath = path.join(repoRoot, 'CHANGELOG.md');
  const changelog = fs.readFileSync(changelogPath, 'utf8');
  const escaped = version.replaceAll('.', '\\.');
  const heading = new RegExp(`^##\\s+\\[?${escaped}\\]?(?:\\s|$)`, 'm');

  if (!heading.test(changelog)) {
    throw new Error(
      `Final release v${version} requires a matching CHANGELOG.md section.`,
    );
  }
}

export function sha256File(filePath) {
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(filePath));
  return hash.digest('hex');
}

export function createReleaseManifest({
  config,
  tag,
  sha,
  platform,
  artifactPath,
}) {
  const parsedTag = parseReleaseTag(tag);
  const platformConfig = config.platforms[platform];
  if (!platformConfig) throw new Error(`Unsupported release platform: ${platform}`);

  if (parsedTag.version !== config.releaseVersion) {
    throw new Error(
      `Tag ${tag} targets ${parsedTag.version}, expected releaseVersion ${config.releaseVersion}.`,
    );
  }

  const epochSeconds = commitEpochSeconds(sha);
  const buildCode = buildCodeFromEpochSeconds(epochSeconds);
  const expectedName = officialArtifactName(config, platform, buildCode);
  const actualName = path.basename(artifactPath);

  if (actualName !== expectedName) {
    throw new Error(
      `Artifact name "${actualName}" does not match official release name "${expectedName}".`,
    );
  }

  return {
    releaseTag: tag,
    version: config.releaseVersion,
    sourceVersion: config.sourceVersion,
    buildCode,
    gitCommit: sha,
    commitTime: new Date(epochSeconds * 1000).toISOString(),
    releaseBranch: config.releaseBranch,
    prerelease: parsedTag.prerelease,
    platform,
    arch: platformConfig.arch,
    artifacts: [
      {
        name: actualName,
        sha256: sha256File(artifactPath),
      },
    ],
  };
}

export function writeGithubOutputs(values) {
  const outputPath = process.env.GITHUB_OUTPUT;
  if (!outputPath) return;

  const lines = Object.entries(values).map(([key, value]) => `${key}=${String(value)}\n`);
  fs.appendFileSync(outputPath, lines.join(''), 'utf8');
}

export function parseCliArgs(argv) {
  const result = {};

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (!arg.startsWith('--')) {
      throw new Error(`Unexpected argument: ${arg}`);
    }

    const key = arg.slice(2);
    const value = argv[index + 1];

    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for --${key}`);
    }

    result[key] = value;
    index += 1;
  }

  return result;
}

function readShellBuilderVersion(relativePath) {
  const source = fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
  const match = source.match(/^APP_VERSION="([^"]+)"/m);
  if (!match) throw new Error(`Missing APP_VERSION in ${relativePath}.`);
  return match[1];
}

function readPowerShellBuilderVersion(relativePath) {
  const source = fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
  const match = source.match(/^\$APP_VERSION = "([^"]+)"/m);
  if (!match) throw new Error(`Missing APP_VERSION in ${relativePath}.`);
  return match[1];
}
