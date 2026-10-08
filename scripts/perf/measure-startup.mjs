#!/usr/bin/env node
/**
 * Measure cold packaged-app startup and warm restart/recovery on a controlled machine.
 * This is intentionally not a shared-runner latency gate.
 */
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir, mkdtemp, rm } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { tmpdir, cpus, totalmem, release, platform, arch, hostname } from 'node:os';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { createServer } from 'node:net';
import { fileURLToPath } from 'node:url';

const args = parseArgs(process.argv.slice(2));
if (!args.app) {
  throw new Error(
    'Usage: node scripts/perf/measure-startup.mjs --app <native launcher> ' +
      '[--runs 3] [--output perf/baselines/startup.json] [--preflight-smoke true]',
  );
}
const app = path.resolve(args.app);
const runs = positiveInteger(args.runs ?? '3', 'runs');
const pollMs = positiveInteger(args['sample-ms'] ?? '250', 'sample-ms');
const timeoutMs = positiveInteger(args['timeout-ms'] ?? '90000', 'timeout-ms');
const preflight = boolean(args['preflight-smoke'] ?? 'true');
const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const outputPath = path.resolve(args.output ?? 'perf/baselines/startup-latest.json');
const dataDir = args['data-dir']
  ? path.resolve(args['data-dir'])
  : await mkdtemp(path.join(tmpdir(), 'rhythmic-riddles-perf-'));
const deleteDataDir = !args['data-dir'] && !boolean(args['keep-data'] ?? 'false');
const reservedPorts = new Set();
const appPort = await choosePort(args['app-port'], 'app-port', reservedPorts);
const managementPort = await choosePort(args['management-port'], 'management-port', reservedPorts);
const embedded = await detectEmbeddedDatabaseMode(app);
if (embedded === undefined) {
  throw new Error('Could not detect packaged production profile from launcher .cfg; use an app-image launcher.');
}
const dbPort = embedded ? await choosePort(args['db-port'], 'db-port', reservedPorts) : undefined;

if (preflight) {
  console.log('Running packaged-product smoke preflight once (not included in timings)...');
  const smokePorts = new Set();
  const smokeAppPort = await choosePort(undefined, 'smoke-app-port', smokePorts);
  const smokeManagementPort = await choosePort(undefined, 'smoke-management-port', smokePorts);
  const smokeDbPort = embedded ? await choosePort(undefined, 'smoke-db-port', smokePorts) : undefined;
  const smoke = spawnSync(
    process.execPath,
    [
      path.join(repositoryRoot, 'scripts/prod/package-smoke.mjs'),
      '--app',
      app,
      '--app-port',
      String(smokeAppPort),
      '--management-port',
      String(smokeManagementPort),
      ...(embedded ? ['--db-port', String(smokeDbPort)] : []),
    ],
    { cwd: repositoryRoot, stdio: 'inherit', env: process.env },
  );
  if (smoke.status !== 0) throw new Error(`package-smoke preflight failed with exit code ${smoke.status}`);
}

await mkdir(dataDir, { recursive: true });
const results = [];
let persistentRoomCode;
try {
  for (let index = 0; index < runs; index += 1) {
    const run = await measureRun(index + 1);
    results.push(run);
    if (index === 0) {
      persistentRoomCode = await createPersistentGame();
    } else {
      await assertRoomExists(persistentRoomCode);
    }
    await stopCleanly(run.child);
    delete run.child;
  }

  const payload = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    build: {
      version: args.version ?? 'PENDING',
      buildCode: args['build-code'] ?? 'PENDING',
      gitSha: args['git-sha'] ?? git('rev-parse', 'HEAD') ?? 'PENDING',
      benchmarkScriptCommit: git('rev-parse', 'HEAD') ?? 'PENDING',
      artifactSha256: args.artifact
        ? await sha256(path.resolve(args.artifact))
        : (args['artifact-sha256'] ?? 'PENDING'),
      launcher: app,
    },
    machine: machineMetadata(),
    methodology: {
      appPort,
      managementPort,
      embeddedDatabase: embedded,
      sampleIntervalMs: pollMs,
      readinessTimeoutMs: timeoutMs,
      preflightPackageSmoke: preflight,
      runCount: runs,
      coldDefinition: 'Run 1: first packaged launch against a fresh isolated APP_DATA_DIR.',
      warmRestartDefinition:
        'Runs 2..N: clean restart against the same APP_DATA_DIR after a persisted game was created.',
      resourceSampling:
        'Aggregate process-tree RSS and %CPU sampled with ps at the configured interval on Linux/macOS.',
    },
    runs: results,
    summary: summarize(results),
  };
  await mkdir(path.dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
  printSummary(payload);
  console.log(`\nJSON evidence: ${path.relative(process.cwd(), outputPath) || outputPath}`);
} finally {
  if (deleteDataDir) await rm(dataDir, { recursive: true, force: true });
}

async function measureRun(runNumber) {
  const startedNs = process.hrtime.bigint();
  const child = spawn(app, [], {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      ...(embedded ? { APP_DB_PORT: String(dbPort) } : {}),
      APP_DATA_DIR: dataDir,
      APP_LOG_DIR: path.join(dataDir, 'logs'),
      SERVER_PORT: String(appPort),
      MANAGEMENT_SERVER_PORT: String(managementPort),
      SPRING_MAIN_BANNER_MODE: 'off',
    },
  });
  let output = '';
  child.stdout.on('data', (chunk) => (output += chunk));
  child.stderr.on('data', (chunk) => (output += chunk));

  let peakRssKiB = 0;
  let peakCpuPercent = 0;
  const samples = [];
  const sampler = setInterval(() => {
    try {
      const sample = sampleTree(child.pid);
      peakRssKiB = Math.max(peakRssKiB, sample.rssKiB);
      peakCpuPercent = Math.max(peakCpuPercent, sample.cpuPercent);
      samples.push({ elapsedMs: elapsedMs(startedNs), ...sample });
    } catch {
      // Readiness/exit logic below is authoritative; a transient process-table race is not.
    }
  }, pollMs);

  try {
    await eventually(async () => {
      if (child.exitCode !== null) throw new Error(`launcher exited ${child.exitCode}`);
      const response = await fetch(`http://127.0.0.1:${managementPort}/actuator/health`);
      if (!response.ok) throw new Error(`health returned ${response.status}`);
      const body = await response.json();
      if (body.status !== 'UP') throw new Error(`health status ${body.status}`);
    }, timeoutMs, `run ${runNumber} readiness`);
  } catch (error) {
    const tail = output.length <= 8000 ? output : output.slice(-8000);
    await forceStop(child);
    throw new Error(`${error}\n--- packaged output tail ---\n${tail}`);
  } finally {
    clearInterval(sampler);
  }

  const finalSample = sampleTree(child.pid);
  peakRssKiB = Math.max(peakRssKiB, finalSample.rssKiB);
  peakCpuPercent = Math.max(peakCpuPercent, finalSample.cpuPercent);
  return {
    run: runNumber,
    kind: runNumber === 1 ? 'cold-start' : 'warm-restart',
    readinessMs: elapsedMs(startedNs),
    peakProcessTreeRssMiB: round(peakRssKiB / 1024, 2),
    peakProcessTreeCpuPercent: round(peakCpuPercent, 2),
    sampleCount: samples.length + 1,
    child,
  };
}

async function createPersistentGame() {
  const response = await fetch(`http://127.0.0.1:${appPort}/api/v1/games`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ maxSongs: 2, maxAlbums: 3 }),
  });
  if (!response.ok) throw new Error(`persistent game creation returned ${response.status}`);
  const body = await response.json();
  if (!/^[A-Z]{4}$/.test(body.roomCode ?? '')) throw new Error('game creation returned no room code');
  return body.roomCode;
}

async function assertRoomExists(roomCode) {
  if (!roomCode) throw new Error('no persisted room code to recover');
  await new Promise((resolve, reject) => {
    const socket = new WebSocket(`ws://127.0.0.1:${appPort}/ws/0${roomCode}`);
    const timeout = setTimeout(() => {
      socket.close();
      reject(new Error(`timed out recovering persisted room ${roomCode}`));
    }, 8000);
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

async function stopCleanly(child) {
  const response = await fetch(`http://127.0.0.1:${managementPort}/actuator/shutdown`, { method: 'POST' });
  if (!response.ok) throw new Error(`Actuator shutdown returned ${response.status}`);
  await waitForExit(child, 20000);
}

async function forceStop(child) {
  if (!child || child.exitCode !== null) return;
  const pids = process.platform === 'win32' ? [child.pid] : processTreePids(child.pid);
  for (const pid of [...pids].reverse()) killIfAlive(pid, 'SIGTERM');
  try {
    await waitForExit(child, 5000);
  } catch {
    for (const pid of [...pids].reverse()) killIfAlive(pid, 'SIGKILL');
  }
}

function sampleTree(rootPid) {
  if (process.platform === 'win32') {
    throw new Error('Resource sampling for the controlled benchmark is implemented for Linux/macOS only.');
  }
  const result = spawnSync('ps', ['-eo', 'pid=,ppid=,rss=,pcpu=,args='], { encoding: 'utf8' });
  if (result.status !== 0) throw new Error(`ps failed: ${result.stderr}`);
  const entries = result.stdout
    .trim()
    .split('\n')
    .map((line) => line.trim().match(/^(\d+)\s+(\d+)\s+(\d+)\s+([\d.]+)\s+(.*)$/))
    .filter(Boolean)
    .map((m) => ({ pid: Number(m[1]), ppid: Number(m[2]), rss: Number(m[3]), cpu: Number(m[4]) }));
  const pids = new Set([rootPid]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const entry of entries) {
      if (pids.has(entry.ppid) && !pids.has(entry.pid)) {
        pids.add(entry.pid);
        changed = true;
      }
    }
  }
  const tree = entries.filter((entry) => pids.has(entry.pid));
  return {
    processCount: tree.length,
    rssKiB: tree.reduce((sum, entry) => sum + entry.rss, 0),
    cpuPercent: tree.reduce((sum, entry) => sum + entry.cpu, 0),
  };
}

function summarize(results) {
  const cold = results.filter((r) => r.kind === 'cold-start').map((r) => r.readinessMs);
  const warm = results.filter((r) => r.kind === 'warm-restart').map((r) => r.readinessMs);
  return {
    coldStartupMs: stats(cold),
    warmRestartMs: stats(warm),
    peakProcessTreeRssMiB: Math.max(...results.map((r) => r.peakProcessTreeRssMiB)),
    peakProcessTreeCpuPercent: Math.max(...results.map((r) => r.peakProcessTreeCpuPercent)),
    persistenceVerifiedOnRestarts: warm.length,
  };
}

function stats(values) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  const median = sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
  return { count: values.length, min: Math.min(...values), median: round(median, 1), max: Math.max(...values) };
}

function printSummary(payload) {
  console.log('\nPackaged startup/recovery measurement');
  console.log('-------------------------------------');
  for (const run of payload.runs) {
    console.log(
      `${String(run.run).padStart(2)} ${run.kind.padEnd(12)} readiness=${run.readinessMs}ms ` +
        `peakRSS=${run.peakProcessTreeRssMiB}MiB peakCPU=${run.peakProcessTreeCpuPercent}%`,
    );
  }
  console.log(`Persistence recovered on ${payload.summary.persistenceVerifiedOnRestarts} warm restart(s).`);
}

function machineMetadata() {
  const cpu = cpus()[0];
  return {
    hostname: hostname(),
    os: `${platform()} ${release()}`,
    arch: arch(),
    cpuModel: cpu?.model ?? 'PENDING',
    logicalCpuCount: cpus().length,
    ramGiB: round(totalmem() / 1024 ** 3, 2),
    powerMode: args['power-mode'] ?? 'PENDING - record before release benchmark',
    backgroundServices: args['background-services'] ?? 'PENDING - record before release benchmark',
    node: process.version,
  };
}

async function sha256(file) {
  const hash = createHash('sha256');
  await new Promise((resolve, reject) => {
    const stream = createReadStream(file);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('end', resolve);
    stream.on('error', reject);
  });
  return hash.digest('hex');
}

function git(...gitArgs) {
  const result = spawnSync('git', gitArgs, { cwd: repositoryRoot, encoding: 'utf8' });
  return result.status === 0 ? result.stdout.trim() : undefined;
}

async function detectEmbeddedDatabaseMode(launcher) {
  const appName = path.basename(launcher).replace(/\.exe$/i, '');
  const launcherDir = path.dirname(launcher);
  const candidates = [
    path.resolve(launcherDir, '..', 'lib', 'app', `${appName}.cfg`),
    path.resolve(launcherDir, 'app', `${appName}.cfg`),
    path.resolve(launcherDir, '..', 'app', `${appName}.cfg`),
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

function eventually(assertion, timeout, label) {
  const deadline = Date.now() + timeout;
  return new Promise(async (resolve, reject) => {
    let failure;
    while (Date.now() < deadline) {
      try {
        await assertion();
        resolve();
        return;
      } catch (error) {
        failure = error;
        await new Promise((r) => setTimeout(r, 250));
      }
    }
    reject(new Error(`${label} failed: ${failure}`));
  });
}

function waitForExit(child, timeout) {
  if (child.exitCode !== null) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('launcher did not exit')), timeout);
    child.once('exit', () => {
      clearTimeout(timer);
      resolve();
    });
  });
}


function processTreePids(rootPid) {
  const result = spawnSync('ps', ['-eo', 'pid=,ppid='], { encoding: 'utf8' });
  if (result.status !== 0) return [rootPid];
  const entries = result.stdout
    .trim()
    .split('\n')
    .map((line) => line.trim().match(/^(\d+)\s+(\d+)$/))
    .filter(Boolean)
    .map((m) => ({ pid: Number(m[1]), ppid: Number(m[2]) }));
  const pids = new Set([rootPid]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const entry of entries) {
      if (pids.has(entry.ppid) && !pids.has(entry.pid)) {
        pids.add(entry.pid);
        changed = true;
      }
    }
  }
  return [...pids];
}

function killIfAlive(pid, signal) {
  try {
    process.kill(pid, signal);
  } catch (error) {
    if (error?.code !== 'ESRCH') throw error;
  }
}

async function choosePort(configured, name, reserved) {
  if (configured !== undefined) {
    const port = tcpPort(configured, name);
    if (reserved.has(port)) throw new Error(`${name} duplicates another configured port: ${port}`);
    reserved.add(port);
    return port;
  }
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const port = await availableLoopbackPort();
    if (!reserved.has(port)) {
      reserved.add(port);
      return port;
    }
  }
  throw new Error(`Could not allocate a unique ${name}`);
}

function availableLoopbackPort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.unref();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : undefined;
      server.close((error) => (error ? reject(error) : resolve(port)));
    });
  });
}

function parseArgs(values) {
  const parsed = {};
  for (let i = 0; i < values.length; i += 1) {
    const argument = values[i];
    if (!argument?.startsWith('--')) throw new Error(`Unexpected argument: ${argument}`);
    const eq = argument.indexOf('=');
    if (eq > 2) {
      parsed[argument.slice(2, eq)] = argument.slice(eq + 1);
      continue;
    }
    const value = values[i + 1];
    if (value === undefined || value.startsWith('--')) throw new Error(`Missing value for ${argument}`);
    parsed[argument.slice(2)] = value;
    i += 1;
  }
  return parsed;
}
function positiveInteger(value, name) {
  const n = Number(value);
  if (!Number.isInteger(n) || n < 1) throw new Error(`${name} must be a positive integer; received ${value}`);
  return n;
}
function tcpPort(value, name) {
  const n = positiveInteger(value, name);
  if (n > 65535) throw new Error(`${name} must be <= 65535`);
  return n;
}
function boolean(value) {
  if (value === true || value === 'true') return true;
  if (value === false || value === 'false') return false;
  throw new Error(`Expected true or false; received ${value}`);
}
function elapsedMs(startedNs) {
  return Math.round(Number(process.hrtime.bigint() - startedNs) / 1e6);
}
function round(value, digits) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}
