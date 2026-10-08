import { WebSocket } from 'k6/websockets';
import {
  ADMIN_CLIENT,
  TV_CLIENT,
  createRoom,
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

const ROOMS = Number(__ENV.ROOMS || 4);
const RECONNECTS = Number(__ENV.RECONNECTS || 3);
const PAUSE_MS = Number(__ENV.RECONNECT_PAUSE_MS || 150);

export const options = {
  scenarios: {
    concurrent_rooms: { executor: 'per-vu-iterations', vus: ROOMS, iterations: 1, maxDuration: '2m' },
  },
  thresholds: reliabilityThresholds,
};

export default function () {
  const roomCode = createRoom({ scenario: 'concurrent-rooms' });
  if (!roomCode) {
    lifecycleFailures.add(true);
    return;
  }
  runPair(0);

  function runPair(attempt) {
    let completed = 0;
    const done = () => {
      completed += 1;
      if (completed !== 2) return;
      if (attempt < RECONNECTS) setTimeout(() => runPair(attempt + 1), PAUSE_MS);
    };
    connectOnce(ADMIN_CLIENT, roomCode, 'admin', attempt, done);
    connectOnce(TV_CLIENT, roomCode, 'tv', attempt, done);
  }
}

function connectOnce(client, roomCode, role, attempt, done) {
  const started = Date.now();
  let welcomes = 0;
  let finished = false;
  const socket = new WebSocket(socketUrl(client, roomCode));

  socket.addEventListener('message', (event) => {
    websocketMessages.add(1, { role });
    const message = parseMessage(event.data);
    if (!verifyWelcome(message)) return;
    welcomes += 1;
    if (welcomes === 1) websocketPropagation.add(Date.now() - started, { role, operation: 'welcome' });
    if (welcomes > 1) websocketDuplicateMessages.add(1, { role, type: 'welcome' });
    finish(welcomes !== 1);
  });
  socket.addEventListener('error', () => {
    websocketConnectionFailures.add(1, { role });
    finish(true);
  });
  socket.addEventListener('close', () => {
    if (!finished) {
      if (welcomes === 0) websocketMissingMessages.add(1, { role, type: 'welcome' });
      finish(welcomes !== 1, false);
    }
  });
  setTimeout(() => {
    if (!finished) {
      websocketMissingMessages.add(1, { role, type: 'welcome' });
      finish(true);
    }
  }, 5000);

  function finish(failed, close = true) {
    if (finished) return;
    finished = true;
    lifecycleFailures.add(failed, { role, attempt: String(attempt) });
    if (close && socket.readyState < 2) socket.close();
    done();
  }
}
