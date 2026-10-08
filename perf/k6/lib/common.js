import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
export const WS_URL = (__ENV.WS_URL || BASE_URL.replace(/^http/, 'ws')).replace(/\/$/, '');
export const ADMIN_CLIENT = __ENV.ADMIN_CLIENT || '0';
export const TV_CLIENT = __ENV.TV_CLIENT || '1';

export const correctnessFailures = new Counter('correctness_failures');
export const roomsCreated = new Counter('rooms_created');
export const websocketMessages = new Counter('websocket_messages');
export const websocketConnectionFailures = new Counter('websocket_connection_failures');
export const websocketMissingMessages = new Counter('websocket_missing_messages');
export const websocketDuplicateMessages = new Counter('websocket_duplicate_messages');
export const websocketPropagation = new Trend('websocket_propagation_ms', true);
export const lifecycleFailures = new Rate('vu_lifecycle_failed');

export const reliabilityThresholds = {
  checks: ['rate==1'],
  http_req_failed: ['rate==0'],
  correctness_failures: ['count==0'],
  websocket_connection_failures: ['count==0'],
  websocket_missing_messages: ['count==0'],
  websocket_duplicate_messages: ['count==0'],
  vu_lifecycle_failed: ['rate==0'],
};

export function createRoom(tags = {}) {
  const response = http.post(
    `${BASE_URL}/api/v1/games`,
    JSON.stringify({ maxSongs: Number(__ENV.MAX_SONGS || 2), maxAlbums: Number(__ENV.MAX_ALBUMS || 3) }),
    { headers: { 'Content-Type': 'application/json' }, tags: { operation: 'create-room', ...tags } },
  );
  const ok = check(response, {
    'create room returns success': (r) => r.status >= 200 && r.status < 300,
    'create room returns roomCode': (r) => /^[A-Z]{4}$/.test(safeJson(r)?.roomCode || ''),
  });
  if (!ok) correctnessFailures.add(1);
  const roomCode = safeJson(response)?.roomCode;
  if (roomCode) roomsCreated.add(1);
  return roomCode;
}

export function socketUrl(client, roomCode) {
  return `${WS_URL}/ws/${client}${roomCode}`;
}

export function safeJson(response) {
  try {
    return response.json();
  } catch {
    return undefined;
  }
}

export function parseMessage(data) {
  try {
    return JSON.parse(String(data));
  } catch {
    correctnessFailures.add(1);
    return undefined;
  }
}

export function verifyWelcome(message) {
  return message?.type === 'welcome' && typeof message?.stage === 'string';
}

export function actionFrame() {
  if (!__ENV.ACTION_MESSAGE_JSON) return undefined;
  try {
    return JSON.parse(__ENV.ACTION_MESSAGE_JSON);
  } catch (error) {
    throw new Error(`ACTION_MESSAGE_JSON must be valid JSON: ${error}`);
  }
}

export function expectedActionType() {
  return __ENV.EXPECTED_ACTION_TYPE || '';
}
