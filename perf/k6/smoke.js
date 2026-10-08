import { check } from 'k6';
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
  websocketMessages,
  websocketMissingMessages,
  websocketPropagation,
  verifyWelcome,
} from './lib/common.js';

export const options = { vus: 1, iterations: 1, thresholds: reliabilityThresholds };

export default function () {
  const roomCode = createRoom({ scenario: 'smoke' });
  if (!roomCode) {
    lifecycleFailures.add(true);
    return;
  }
  openUntilWelcome(ADMIN_CLIENT, roomCode, 'admin');
  openUntilWelcome(TV_CLIENT, roomCode, 'tv');
}

function openUntilWelcome(client, roomCode, role) {
  const started = Date.now();
  let welcome = false;
  let finished = false;
  const socket = new WebSocket(socketUrl(client, roomCode));

  socket.addEventListener('open', () => {
    check(true, { [`${role} websocket opened`]: (value) => value });
  });
  socket.addEventListener('message', (event) => {
    websocketMessages.add(1, { role });
    const message = parseMessage(event.data);
    if (!verifyWelcome(message)) return;
    welcome = true;
    websocketPropagation.add(Date.now() - started, { role, operation: 'welcome' });
    check(message, { [`${role} receives welcome`]: verifyWelcome });
    finish(false);
  });
  socket.addEventListener('error', () => {
    websocketConnectionFailures.add(1, { role });
    finish(true);
  });
  socket.addEventListener('close', () => {
    if (!welcome && !finished) {
      websocketMissingMessages.add(1, { role, type: 'welcome' });
      finish(true, false);
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
    lifecycleFailures.add(failed, { role, scenario: 'smoke' });
    if (close && socket.readyState < 2) socket.close();
  }
}
