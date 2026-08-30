import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const repo = path.resolve(process.cwd(), '../..');
const frontend = JSON.parse(fs.readFileSync(path.join(repo, 'apps/frontend/package.json'), 'utf8'));
const lockfile = JSON.parse(
  fs.readFileSync(path.join(repo, 'apps/frontend/package-lock.json'), 'utf8'),
);
const pom = fs.readFileSync(path.join(repo, 'apps/backend/pom.xml'), 'utf8');

describe('release build contracts', () => {
  it('keeps Maven Node and npm inside the frontend supported ranges', () => {
    const node = xmlValue('nodeVersion').replace(/^v/, '');
    const npm = xmlValue('npmVersion');

    expect(satisfiesSimpleRange(node, frontend.engines.node)).toBe(true);
    expect(satisfiesSimpleRange(npm, frontend.engines.npm)).toBe(true);
    expect(lockfile.packages[''].engines).toEqual(frontend.engines);

    const angularNodeRange = lockfile.packages['node_modules/@angular/cli'].engines.node as string;
    const supportedMinimum = minimumForMajor(angularNodeRange, Number(node.split('.')[0]));
    expect(compareVersions(node, supportedMinimum)).toBeGreaterThanOrEqual(0);
  });

  it('keeps platform selection in every external-DB builder', () => {
    assertShellBuilder('linux', 'scripts/prod/build/build_linux_jpackage.sh');
    assertShellBuilder('macos', 'scripts/prod/build/build_macos_jpackage.sh');
    assertPowerShellBuilder('windows', 'scripts/prod/build/build_windows_jpackage.ps1');
  });

  it('rejects invalid embeddb values in every native builder', () => {
    const linux = fs.readFileSync(
      path.join(repo, 'scripts/prod/build/build_linux_jpackage.sh'),
      'utf8',
    );
    const macos = fs.readFileSync(
      path.join(repo, 'scripts/prod/build/build_macos_jpackage.sh'),
      'utf8',
    );
    const windows = fs.readFileSync(
      path.join(repo, 'scripts/prod/build/build_windows_jpackage.ps1'),
      'utf8',
    );

    expect(linux).toContain('embeddb must be true or false');
    expect(macos).toContain('embeddb must be true or false');
    expect(windows).toContain('embeddb must be true or false');
  });

  it('keeps native package versions aligned with the backend release version', () => {
    const backendVersion = projectVersion();
    expect(frontend.version).toBe(backendVersion);
    expect(lockfile.packages[''].version).toBe(backendVersion);
    expect(shellAppVersion('scripts/prod/build/build_linux_jpackage.sh')).toBe(backendVersion);
    expect(shellAppVersion('scripts/prod/build/build_macos_jpackage.sh')).toBe(backendVersion);
    expect(powerShellAppVersion('scripts/prod/build/build_windows_jpackage.ps1')).toBe(
      backendVersion,
    );
  });

  it('fails the Windows builder when required native commands return non-zero', () => {
    const source = fs.readFileSync(
      path.join(repo, 'scripts/prod/build/build_windows_jpackage.ps1'),
      'utf8',
    );

    expect(source).toMatch(/& mvn @MVN_ARGS[\s\S]*?\$LASTEXITCODE -ne 0/);
    expect(source).toMatch(/--type app-image[\s\S]*?\$LASTEXITCODE -ne 0/);
    expect(source).toMatch(/--type msi[\s\S]*?\$LASTEXITCODE -ne 0/);
  });
});

function assertShellBuilder(platform: string, file: string): void {
  const source = fs.readFileSync(path.join(repo, file), 'utf8');
  expect(source).toContain(`MVN_ARGS=("-Dplatform=${platform}" "-Dembeddb=false")`);
  expect(source).toContain('"${MVN_ARGS[@]}"');
  expect(source).toContain('-DskipTests');
  expect(source).not.toMatch(/mvn[^\n]+spring\.profiles\.active/);
  expect(source).toContain('jpackage');
  expect(source).toContain('app.assets.base-dir');
}

function assertPowerShellBuilder(platform: string, file: string): void {
  const source = fs.readFileSync(path.join(repo, file), 'utf8');
  expect(source).toContain(`$MVN_ARGS = @("-Pproduction", "-Dplatform=${platform}")`);
  expect(source).toContain(
    `$MVN_ARGS = @("-Pproduction", "-Dplatform=${platform}", "-Dembeddb=false")`,
  );
  expect(source).toContain('& mvn @MVN_ARGS');
  expect(source).toContain('& mvn @MVN_ARGS "-DskipTests" clean package');
  expect(source).not.toMatch(/& mvn[^\n]+spring\.profiles\.active/);
  expect(source).toContain('jpackage');
  expect(source).toContain('app.assets.base-dir');
}

function projectVersion(): string {
  const project = pom.match(/<artifactId>cestereg<\/artifactId>\s*<version>([^<]+)<\/version>/);
  if (!project) throw new Error('Missing backend project version in apps/backend/pom.xml');
  return project[1].trim();
}

function shellAppVersion(file: string): string {
  const source = fs.readFileSync(path.join(repo, file), 'utf8');
  const match = source.match(/^APP_VERSION="([^"]+)"/m);
  if (!match) throw new Error(`Missing APP_VERSION in ${file}`);
  return match[1];
}

function powerShellAppVersion(file: string): string {
  const source = fs.readFileSync(path.join(repo, file), 'utf8');
  const match = source.match(/^\$APP_VERSION = "([^"]+)"/m);
  if (!match) throw new Error(`Missing APP_VERSION in ${file}`);
  return match[1];
}

function xmlValue(tag: string): string {
  const match = pom.match(new RegExp(`<${tag}>([^<]+)</${tag}>`));
  if (!match) throw new Error(`Missing ${tag} in apps/backend/pom.xml`);
  return match[1].trim();
}

function minimumForMajor(range: string, major: number): string {
  const match = range.match(new RegExp(`(?:\\^|>=)${major}\\.(\\d+)\\.(\\d+)`));
  if (!match) throw new Error(`Angular does not declare a supported Node ${major} line: ${range}`);
  return `${major}.${match[1]}.${match[2]}`;
}

function compareVersions(left: string, right: string): number {
  const leftParts = left.split('.').map(Number);
  const rightParts = right.split('.').map(Number);
  for (let index = 0; index < 3; index++) {
    if (leftParts[index] !== rightParts[index]) return leftParts[index] - rightParts[index];
  }
  return 0;
}

function satisfiesSimpleRange(version: string, range: string): boolean {
  return range.split(/\s+/).every((constraint) => {
    if (constraint.startsWith('>='))
      return compareVersions(version, normalizeVersion(constraint.slice(2))) >= 0;
    if (constraint.startsWith('<'))
      return compareVersions(version, normalizeVersion(constraint.slice(1))) < 0;
    throw new Error(`Unsupported engine constraint: ${constraint}`);
  });
}

function normalizeVersion(version: string): string {
  const parts = version.split('.');
  while (parts.length < 3) parts.push('0');
  return parts.slice(0, 3).join('.');
}
