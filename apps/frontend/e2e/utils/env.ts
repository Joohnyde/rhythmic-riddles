export const FRONTEND_URL = process.env.E2E_FRONTEND_URL ?? 'http://localhost:4200';
export const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

export type Role = 'admin' | 'tv';

export const TV_CONNECTED_ROUTE = /\/(lobby|albums|songs|winner)$/;
export const ADMIN_CONNECTED_ROUTE = /\/admin\/(lobby|albums|songs|winner)$/;
export const CONNECTED_ROUTE = /\/(admin\/)?(lobby|albums|songs|winner)$/;

export function wsUrlFor(role: Role, roomCode: string): string {
  const backend = new URL(BACKEND_URL);
  const protocol = backend.protocol === 'https:' ? 'wss:' : 'ws:';
  const position = role === 'admin' ? '0' : '1';
  return `${protocol}//${backend.host}/ws/${position}${roomCode}`;
}

export function pagePathFor(role: Role): string {
  return role === 'admin' ? '/admin' : '/';
}

export function connectedRouteFor(role: Role): RegExp {
  return role === 'admin' ? ADMIN_CONNECTED_ROUTE : TV_CONNECTED_ROUTE;
}
