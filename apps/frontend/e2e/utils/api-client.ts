import { APIRequestContext, expect } from '@playwright/test';
import { BACKEND_URL } from './env';

export type Team = { id: string; name: string; image?: string; buttonCode?: string };

export async function createRoom(request: APIRequestContext): Promise<string> {
  const response = await request.post(`${BACKEND_URL}/api/v1/games`, {
    data: { maxSongs: 10, maxAlbums: 10 },
  });
  expect(
    response.ok(),
    `create room failed: ${response.status()} ${await response.text()}`,
  ).toBeTruthy();
  const body = (await response.json()) as { roomCode?: string };
  expect(body.roomCode).toBeTruthy();
  return body.roomCode!;
}

export async function createTeam(
  request: APIRequestContext,
  roomCode: string,
  name = `Team ${Date.now()}`,
): Promise<Team> {
  const response = await request.post(`${BACKEND_URL}/api/v1/games/${roomCode}/teams`, {
    data: {
      name,
      buttonCode: `BTN-${Date.now()}-${Math.random()}`,
      image: `https://example.com/${Date.now()}.png`,
    },
  });
  expect(
    response.ok(),
    `create team failed: ${response.status()} ${await response.text()}`,
  ).toBeTruthy();
  return (await response.json()) as Team;
}

export async function deleteTeam(
  request: APIRequestContext,
  roomCode: string,
  teamId: string,
): Promise<void> {
  const response = await request.delete(`${BACKEND_URL}/api/v1/games/${roomCode}/teams/${teamId}`);
  expect(
    response.ok(),
    `delete team failed: ${response.status()} ${await response.text()}`,
  ).toBeTruthy();
}

export async function tryCreateInvalidTeam(
  request: APIRequestContext,
  roomCode: string,
): Promise<number> {
  const response = await request.post(`${BACKEND_URL}/api/v1/games/${roomCode}/teams`, {
    data: { name: '', buttonCode: '', image: '' },
  });
  return response.status();
}

export async function pickAlbum(
  request: APIRequestContext,
  roomCode: string,
  categoryId: string,
  teamId: string | null,
): Promise<number> {
  const response = await request.put(
    `${BACKEND_URL}/api/v1/games/${roomCode}/categories/${categoryId}/pick`,
    { data: { teamId } },
  );
  return response.status();
}

export async function startCategory(
  request: APIRequestContext,
  roomCode: string,
  categoryId: string,
): Promise<number> {
  const response = await request.post(
    `${BACKEND_URL}/api/v1/games/${roomCode}/categories/${categoryId}/start`,
  );
  return response.status();
}

export async function createInterrupt(
  request: APIRequestContext,
  roomCode: string,
  teamId: string | null,
): Promise<number> {
  const response = await request.post(`${BACKEND_URL}/api/v1/games/${roomCode}/interrupts`, {
    data: { teamId },
  });
  return response.status();
}

export async function answerInterrupt(
  request: APIRequestContext,
  roomCode: string,
  answerId: string,
  correct: boolean,
): Promise<number> {
  const response = await request.post(
    `${BACKEND_URL}/api/v1/games/${roomCode}/interrupts/${answerId}/answer`,
    { data: { correct } },
  );
  return response.status();
}

export async function resolveSystemInterrupt(
  request: APIRequestContext,
  roomCode: string,
  scheduleId: string,
): Promise<number> {
  const response = await request.post(
    `${BACKEND_URL}/api/v1/games/${roomCode}/interrupts/system/resolve`,
    { data: { scheduleId } },
  );
  return response.status();
}

export async function replaySchedule(
  request: APIRequestContext,
  roomCode: string,
  scheduleId: string,
): Promise<number> {
  const response = await request.post(
    `${BACKEND_URL}/api/v1/games/${roomCode}/schedules/${scheduleId}/replay`,
  );
  return response.status();
}

export async function revealSchedule(
  request: APIRequestContext,
  roomCode: string,
  scheduleId: string,
): Promise<number> {
  const response = await request.post(
    `${BACKEND_URL}/api/v1/games/${roomCode}/schedules/${scheduleId}/reveal`,
  );
  return response.status();
}

export async function nextSchedule(request: APIRequestContext, roomCode: string): Promise<number> {
  const response = await request.post(`${BACKEND_URL}/api/v1/games/${roomCode}/schedules/next`);
  return response.status();
}
