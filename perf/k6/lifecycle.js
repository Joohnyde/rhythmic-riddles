import { WebSocket } from 'k6/websockets';
import {
  ADMIN_CLIENT,
  TV_CLIENT,
  actionFrame,
  createRoom,
  expectedActionType,
  lifecycleFailures,
  parseMessage,
  reliabilityThresholds,
  socketUrl,
  websocketConnectionFailures,
  websocketDuplicateMessages,
  websocketMessages,
  websocketMissingMessages,
  websocketPropagation,
  verifyWelcome,
} from './lib/common.js';

const GAMES_PER_VU = Number(__ENV.GAMES_PER_VU || 5);
const ACTION_BURST = Number(__ENV.ACTION_BURST || 0);
const BETWEEN_GAMES_MS = Number(__ENV.BETWEEN_GAMES_MS || 100);
const action = actionFrame();
const expectedType = expectedActionType();
const EXPECTED_ACTION_FRAMES = Number(__ENV.EXPECTED_ACTION_FRAMES || ACTION_BURST);

if (ACTION_BURST > 0 && (!action || !expectedType || EXPECTED_ACTION_FRAMES < 1)) {
  throw new Error(
    'ACTION_BURST > 0 requires ACTION_MESSAGE_JSON, EXPECTED_ACTION_TYPE, and a positive EXPECTED_ACTION_FRAMES (defaults to ACTION_BURST).',
  );
}

export const options = {
  vus: Number(__ENV.VUS || 2),
  iterations: Number(__ENV.ITERATIONS || 2),
  thresholds: reliabilityThresholds,
};

export default function () {
  runGame(0);

  function runGame(index) {
    if (index >= GAMES_PER_VU) return;
    const roomCode = createRoom({ scenario: 'lifecycle' });
    if (!roomCode) {
      lifecycleFailures.add(true, { phase: 'create-room' });
      setTimeout(() => runGame(index + 1), BETWEEN_GAMES_MS);
      return;
    }
    exerciseRoom(roomCode, () => setTimeout(() => runGame(index + 1), BETWEEN_GAMES_MS));
  }
}

function exerciseRoom(roomCode, done) {
  let adminReady = false;
  let tvReady = false;
  let expectedFrames = 0;
  let firstActionAt = 0;
  let finished = false;
  let actionSent = false;

  const admin = new WebSocket(socketUrl(ADMIN_CLIENT, roomCode));
  const tv = new WebSocket(socketUrl(TV_CLIENT, roomCode));

  admin.addEventListener('message', (event) => {
    websocketMessages.add(1, { role: 'admin' });
    const message = parseMessage(event.data);
    if (verifyWelcome(message)) {
      adminReady = true;
      maybeAct();
    }
  });
  tv.addEventListener('message', (event) => {
    websocketMessages.add(1, { role: 'tv' });
    const message = parseMessage(event.data);
    if (verifyWelcome(message)) {
      tvReady = true;
      maybeAct();
      if (ACTION_BURST === 0) finish(false);
      return;
    }
    if (ACTION_BURST > 0 && message?.type === expectedType) {
      expectedFrames += 1;
      if (expectedFrames === 1) websocketPropagation.add(Date.now() - firstActionAt, { operation: expectedType });
      if (expectedFrames > EXPECTED_ACTION_FRAMES) websocketDuplicateMessages.add(1, { type: expectedType });
      if (expectedFrames >= EXPECTED_ACTION_FRAMES) finish(false);
    }
  });
  admin.addEventListener('error', () => failConnection('admin'));
  tv.addEventListener('error', () => failConnection('tv'));

  setTimeout(() => {
    if (finished) return;
    if (!adminReady) websocketMissingMessages.add(1, { role: 'admin', type: 'welcome' });
    if (!tvReady) websocketMissingMessages.add(1, { role: 'tv', type: 'welcome' });
    if (ACTION_BURST > 0 && expectedFrames < EXPECTED_ACTION_FRAMES) {
      websocketMissingMessages.add(EXPECTED_ACTION_FRAMES - expectedFrames, { role: 'tv', type: expectedType });
    }
    finish(true);
  }, Number(__ENV.ROOM_TIMEOUT_MS || 8000));

  function maybeAct() {
    if (finished || actionSent || !adminReady || !tvReady) return;
    if (ACTION_BURST === 0) return;
    actionSent = true;
    firstActionAt = Date.now();
    for (let index = 0; index < ACTION_BURST; index += 1) admin.send(JSON.stringify(action));
  }

  function failConnection(role) {
    websocketConnectionFailures.add(1, { role });
    finish(true);
  }

  function finish(failed) {
    if (finished) return;
    finished = true;
    lifecycleFailures.add(failed, { scenario: 'lifecycle' });
    if (admin.readyState < 2) admin.close();
    if (tv.readyState < 2) tv.close();
    done();
  }
}
