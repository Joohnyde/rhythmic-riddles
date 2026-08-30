#!/usr/bin/env node
/* Native packaged-product smoke runner. The image must be built on the current OS. */
import { access, mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { fileURLToPath } from 'node:url';

const options = parseArgs(process.argv.slice(2));
const app = options.app;
if (!app) {
  throw new Error(
    'Usage: node scripts/prod/package-smoke.mjs --app <native launcher> ' +
      '[--app-port 18080] [--management-port 18081] [--db-port <embedded-db-port>]',
  );
}

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const requireFromFrontend = createRequire(path.join(repositoryRoot, 'apps/frontend/package.json'));
const appPort = integer(options['app-port'] ?? '18080', 'app-port');
const managementPort = integer(options['management-port'] ?? '18081', 'management-port');
const embeddb = await detectEmbeddedDatabaseMode(app);
if (embeddb === undefined) {
  throw new Error(
    'Could not determine whether this package uses embedded PostgreSQL from its launcher configuration. ' +
      'The package must declare either production or production,embeddb as its active Spring profile.',
  );
}

if (!embeddb && options['db-port'] !== undefined) {
  throw new Error(
    '--db-port controls only the bundled PostgreSQL port for packages built with embeddb=true. ' +
      'For an external database, use application-production.yml or APP_DB_HOST/APP_DB_PORT/' +
      'APP_DB_DATABASE/APP_DB_USERNAME/APP_DB_PASSWORD environment overrides.',
  );
}
const dbPort = embeddb
  ? options['db-port']
    ? integer(options['db-port'], 'db-port')
    : await availableLoopbackPort()
  : undefined;

const dataDir = await mkdtemp(path.join(tmpdir(), 'rhythmic-riddles-package-smoke-'));
let child;
let output = '';
let observedProcessIds = [];
let persistentRoomCode;

try {
  await start();
  await assertDatabaseInitialized();
  await assertProductionHttpContract();
  await assertAngularBoots();
  persistentRoomCode = await createPersistentGame();
  await assertRoomExists(persistentRoomCode);
  await stopCleanly();

  await start();
  await assertDatabaseInitialized();
  await assertRoomExists(persistentRoomCode);
  await stopCleanly();

  console.log(
    `Package smoke passed (${embeddb ? 'embedded DB' : 'configured external DB'}): ` +
      `${embeddb ? 'bootstrap/schema' : 'schema compatibility'}, persistence, Angular, media and cleanup.`,
  );
} catch (error) {
  const tail = output.length <= 8_000 ? output : output.slice(-8_000);
  throw new Error(
    `${error instanceof Error ? error.message : error}\n--- packaged output tail ---\n${tail}`,
  );
} finally {
  await forceStop();
  await rm(dataDir, { recursive: true, force: true });
}

async function start() {
  output = '';
  // Passing application arguments to a jpackage launcher replaces (rather than appends to) the
  // packaged default arguments. Environment overrides preserve production(/embeddb) and $APPDIR assets.
  const databaseEnvironment = embeddb
    ? { APP_DB_PORT: String(dbPort) }
    : {};

  child = spawn(app, [], {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      ...databaseEnvironment,
      APP_DATA_DIR: dataDir,
      APP_LOG_DIR: path.join(dataDir, 'logs'),
      SERVER_PORT: String(appPort),
      MANAGEMENT_SERVER_PORT: String(managementPort),
      SPRING_MAIN_BANNER_MODE: 'off',
    },
  });
  child.stdout.on('data', (chunk) => {
    output += chunk;
  });
  child.stderr.on('data', (chunk) => {
    output += chunk;
  });

  await eventually(
    async () => {
      if (child.exitCode !== null) throw new Error(`launcher exited ${child.exitCode}`);
      const response = await fetch(`http://127.0.0.1:${managementPort}/actuator/health`);
      if (!response.ok) throw new Error(`Actuator health returned ${response.status}`);
      const body = await response.json();
      if (body.status !== 'UP') throw new Error(`Actuator status was ${body.status}`);
    },
    60_000,
    'packaged application readiness',
  );

  observedProcessIds = await descendantProcessIds(child.pid);
}

async function assertDatabaseInitialized() {
  if (!embeddb) return;
  const marker = path.join(dataDir, 'postgres', 'data', '.cestereg_sql_done');
  await access(marker);
}

async function assertProductionHttpContract() {
  await assertOk('/', 'text/html');

  // application-production.yml intentionally disables both endpoints. SPA fallback may answer
  // these paths with Angular HTML, so status alone cannot prove the production policy.
  const openApi = await fetch(`http://127.0.0.1:${appPort}/openapi`);
  if (openApi.headers.get('content-type')?.includes('application/json')) {
    throw new Error('Production unexpectedly exposed the OpenAPI document.');
  }
  const swagger = await fetch(`http://127.0.0.1:${appPort}/swagger`);
  if ((await swagger.text()).toLowerCase().includes('swagger ui')) {
    throw new Error('Production unexpectedly exposed Swagger UI.');
  }
}

async function assertAngularBoots() {
  const { chromium } = requireFromFrontend('@playwright/test');
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage();
    const pageErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    await page.goto(`http://127.0.0.1:${appPort}/`, {
      waitUntil: 'domcontentloaded',
    });
    await page.getByTestId('tv-home-page').waitFor({ state: 'visible' });
    await page.getByTestId('tv-login-form').waitFor({ state: 'visible' });
    await assertBrowserMedia(page);
    await page.goto(`http://127.0.0.1:${appPort}/admin`, {
      waitUntil: 'domcontentloaded',
    });
    await page.getByTestId('admin-home-page').waitFor({ state: 'visible' });
    await page.getByTestId('admin-login-form').waitFor({ state: 'visible' });
    if (pageErrors.length > 0) throw new Error(`Angular page errors: ${pageErrors.join('; ')}`);
  } finally {
    await browser.close();
  }
}

async function assertBrowserMedia(page) {
  const teamIcon = await representativeTeamIconResource();
  const expectations = [
    {
      resource: '/assets/v1/audio/snippets/c041398e-8e63-40ed-8f17-d7f1ca8ca405',
      contentType: 'audio/mpeg',
    },
    {
      resource: '/assets/v1/audio/answers/c041398e-8e63-40ed-8f17-d7f1ca8ca405',
      contentType: 'audio/mpeg',
    },
    {
      resource: '/assets/v1/image/albums/6214ed07-03df-41c7-a1fa-b1a9b9e9bd01',
      contentType: 'image/',
    },
    {
      resource: teamIcon,
      contentType: 'image/',
    },
  ];

  const results = await page.evaluate(async (items) => {
    return Promise.all(
      items.map(async ({ resource, contentType }) => {
        const response = await fetch(resource);
        const body = await response.arrayBuffer();
        return {
          resource,
          contentType,
          status: response.status,
          actualContentType: response.headers.get('content-type') ?? '',
          byteLength: body.byteLength,
        };
      }),
    );
  }, expectations);

  for (const result of results) {
    if (
      result.status < 200 ||
      result.status >= 300 ||
      !result.actualContentType.includes(result.contentType) ||
      result.byteLength === 0
    ) {
      throw new Error(
        `${result.resource} browser fetch expected ${result.contentType}; got ` +
          `${result.status} ${result.actualContentType} (${result.byteLength} bytes)`,
      );
    }
  }
}

async function representativeTeamIconResource() {
  const directory = path.join(repositoryRoot, 'apps/frontend/public/team-icons');
  const icons = (await readdir(directory))
    .filter((file) => /\.(?:png|jpg)$/i.test(file))
    .sort((left, right) => left.localeCompare(right));
  if (icons.length === 0) {
    throw new Error(`No source team icons found in ${directory}`);
  }
  return `/team-icons/${encodeURIComponent(icons[0])}`;
}

async function createPersistentGame() {
  const response = await fetch(`http://127.0.0.1:${appPort}/api/v1/games`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ maxSongs: 2, maxAlbums: 3 }),
  });
  if (!response.ok) throw new Error(`persistent game creation returned ${response.status}`);
  const body = await response.json();
  if (!/^[A-Z]{4}$/.test(body.roomCode ?? '')) throw new Error('creation returned no room code');
  return body.roomCode;
}

async function assertRoomExists(roomCode) {
  await new Promise((resolve, reject) => {
    const socket = new WebSocket(`ws://127.0.0.1:${appPort}/ws/0${roomCode}`);
    const timeout = setTimeout(() => {
      socket.close();
      reject(new Error(`timed out recovering persisted room ${roomCode}`));
    }, 8_000);
    socket.onerror = () => {
      clearTimeout(timeout);
      reject(new Error(`persisted room ${roomCode} rejected its Admin socket`));
    };
    socket.onmessage = (event) => {
      const message = JSON.parse(String(event.data));
      if (message.type === 'welcome' && message.stage === 'lobby') {
        clearTimeout(timeout);
        socket.close();
        resolve();
      }
    };
  });
}

async function stopCleanly() {
  if (!child || child.exitCode !== null) throw new Error('packaged launcher stopped unexpectedly');
  const response = await fetch(`http://127.0.0.1:${managementPort}/actuator/shutdown`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`Actuator shutdown returned ${response.status}`);
  await waitForExit(child, 20_000);
  await eventually(
    async () => {
      const alive = observedProcessIds.filter(isProcessAlive);
      if (alive.length > 0) throw new Error(`processes still alive: ${alive.join(', ')}`);
      const related = await processesMentioning(dataDir);
      if (related.length > 0) throw new Error(`data-directory processes still alive: ${related}`);
    },
    10_000,
    embeddb ? 'Java/embedded PostgreSQL cleanup' : 'packaged Java cleanup',
  );
  child = undefined;
}

async function forceStop() {
  const candidates = new Set(observedProcessIds);
  if (child?.pid && child.exitCode === null) {
    for (const pid of await descendantProcessIds(child.pid)) candidates.add(pid);
    child.kill('SIGTERM');
    try {
      await waitForExit(child, 5_000);
    } catch {
      child.kill('SIGKILL');
    }
  }

  const remaining = [...candidates].filter((pid) => pid !== process.pid && isProcessAlive(pid));
  for (const pid of remaining) killIfAlive(pid, 'SIGTERM');
  try {
    await eventually(
      async () => {
        const alive = remaining.filter(isProcessAlive);
        if (alive.length > 0) throw new Error(`processes still alive: ${alive.join(', ')}`);
      },
      5_000,
      'forced package cleanup',
    );
  } catch {
    for (const pid of remaining.filter(isProcessAlive)) killIfAlive(pid, 'SIGKILL');
  }
  child = undefined;
}

async function assertOk(resource, contentType) {
  const response = await fetch(`http://127.0.0.1:${appPort}${resource}`);
  if (!response.ok || !response.headers.get('content-type')?.includes(contentType)) {
    throw new Error(
      `${resource} expected ${contentType}; got ${response.status} ${response.headers.get('content-type')}`,
    );
  }
  if ((await response.arrayBuffer()).byteLength === 0) throw new Error(`${resource} was empty`);
}

async function eventually(assertion, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs;
  let failure;
  while (Date.now() < deadline) {
    try {
      await assertion();
      return;
    } catch (error) {
      failure = error;
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
  }
  throw new Error(`${label} failed: ${failure}`);
}

function waitForExit(process, timeoutMs) {
  if (process.exitCode !== null) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('launcher did not exit')), timeoutMs);
    process.once('exit', () => {
      clearTimeout(timeout);
      resolve();
    });
  });
}

async function descendantProcessIds(rootPid) {
  const processes = processTable();
  const descendants = [];
  const queue = [rootPid];
  while (queue.length > 0) {
    const parent = queue.shift();
    for (const candidate of processes.filter((entry) => entry.parentPid === parent)) {
      descendants.push(candidate.pid);
      queue.push(candidate.pid);
    }
  }
  return [rootPid, ...descendants];
}

async function processesMentioning(text) {
  return processTable()
    .filter((entry) => entry.command.includes(text))
    .map((entry) => entry.pid);
}

function processTable() {
  if (process.platform === 'win32') {
    const command =
      'Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId,CommandLine | ConvertTo-Json -Compress';
    const result = spawnSync('powershell', ['-NoProfile', '-Command', command], {
      encoding: 'utf8',
    });
    if (result.status !== 0)
      throw new Error(`Could not inspect Windows processes: ${result.stderr}`);
    const parsed = JSON.parse(result.stdout || '[]');
    return (Array.isArray(parsed) ? parsed : [parsed]).map((entry) => ({
      pid: Number(entry.ProcessId),
      parentPid: Number(entry.ParentProcessId),
      command: String(entry.CommandLine ?? ''),
    }));
  }
  const result = spawnSync('ps', ['-eo', 'pid=,ppid=,args='], {
    encoding: 'utf8',
  });
  if (result.status !== 0) throw new Error(`Could not inspect processes: ${result.stderr}`);
  return result.stdout
    .trim()
    .split('\n')
    .map((line) => line.trim().match(/^(\d+)\s+(\d+)\s+(.*)$/))
    .filter(Boolean)
    .map((match) => ({
      pid: Number(match[1]),
      parentPid: Number(match[2]),
      command: match[3],
    }));
}

function isProcessAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function killIfAlive(pid, signal) {
  try {
    process.kill(pid, signal);
  } catch (error) {
    if (error?.code !== 'ESRCH') throw error;
  }
}

function parseArgs(values) {
  const parsed = {};
  for (let index = 0; index < values.length; index += 1) {
    const argument = values[index];
    if (!argument?.startsWith('--')) {
      throw new Error('Arguments must be supplied as --name value or --name=value.');
    }
    const equalsIndex = argument.indexOf('=');
    if (equalsIndex > 2) {
      parsed[argument.slice(2, equalsIndex)] = argument.slice(equalsIndex + 1);
      continue;
    }
    const value = values[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`Missing value for ${argument}.`);
    }
    parsed[argument.slice(2)] = value;
    index += 1;
  }
  return parsed;
}

function integer(value, name) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65_535) {
    throw new Error(`${name} must be a TCP port; received ${value}`);
  }
  return parsed;
}

async function detectEmbeddedDatabaseMode(launcher) {
  const appName = path.basename(launcher).replace(/\.exe$/i, '');
  const launcherDir = path.dirname(path.resolve(launcher));
  const candidates = [
    path.resolve(launcherDir, '..', 'lib', 'app', `${appName}.cfg`), // Linux app-image
    path.resolve(launcherDir, 'app', `${appName}.cfg`), // Windows app-image
    path.resolve(launcherDir, '..', 'app', `${appName}.cfg`), // macOS Contents/MacOS -> Contents/app
  ];

  for (const candidate of candidates) {
    try {
      const content = await readFile(candidate, 'utf8');
      if (content.includes('spring.profiles.active=production,embeddb')) return true;
      if (content.includes('spring.profiles.active=production')) return false;
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }
  }
  return undefined;
}

function availableLoopbackPort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : undefined;
      server.close((error) => {
        if (error) reject(error);
        else if (!port) reject(new Error('Could not allocate an isolated PostgreSQL port.'));
        else resolve(port);
      });
    });
  });
}
