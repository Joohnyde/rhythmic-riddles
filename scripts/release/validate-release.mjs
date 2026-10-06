#!/usr/bin/env node

import {
  assertCommitOnReleaseBranch,
  assertFinalChangelog,
  assertSourceVersionContract,
  buildCodeFromEpochSeconds,
  commitEpochSeconds,
  loadReleaseConfig,
  parseCliArgs,
  parseReleaseTag,
  resolveGitSha,
  resolveReleaseTag,
  writeGithubOutputs,
} from './release-lib.mjs';

try {
  const args = parseCliArgs(process.argv.slice(2));
  const config = loadReleaseConfig();
  const tag = resolveReleaseTag(args.tag);
  const sha = resolveGitSha(args.sha);
  const parsed = parseReleaseTag(tag);

  if (parsed.version !== config.releaseVersion) {
    throw new Error(
      `Release tag ${tag} targets ${parsed.version}, but release-config.json targets ` +
        `${config.releaseVersion}.`,
    );
  }

  assertSourceVersionContract(config);
  assertCommitOnReleaseBranch(sha, config.releaseBranch);

  if (!parsed.prerelease) {
    assertFinalChangelog(config.releaseVersion);
  }

  const epochSeconds = commitEpochSeconds(sha);
  const buildCode = buildCodeFromEpochSeconds(epochSeconds);
  const commitTime = new Date(epochSeconds * 1000).toISOString();

  console.log('[release] validation passed');
  console.log(`[release] tag:            ${tag}`);
  console.log(`[release] release version:${config.releaseVersion}`);
  console.log(`[release] source version: ${config.sourceVersion}`);
  console.log(`[release] branch:         ${config.releaseBranch}`);
  console.log(`[release] commit:         ${sha}`);
  console.log(`[release] commit time:    ${commitTime}`);
  console.log(`[release] build code:     ${buildCode}`);
  console.log(`[release] prerelease:     ${parsed.prerelease}`);

  writeGithubOutputs({
    tag,
    release_version: config.releaseVersion,
    source_version: config.sourceVersion,
    release_branch: config.releaseBranch,
    sha,
    commit_time: commitTime,
    build_code: buildCode,
    prerelease: parsed.prerelease,
  });
} catch (error) {
  console.error(`[release] validation failed: ${error.message}`);
  process.exit(1);
}
