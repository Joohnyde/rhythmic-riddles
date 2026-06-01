import { APIRequestContext, expect, test } from '@playwright/test';
import { BACKEND_URL } from './env';
import { deleteGameFixture } from './fixture-api';

export type DeterministicFixtureType =
  | 'EXPIRED_NO_PAUSE'
  | 'TEAM_PAUSED'
  | 'SYSTEM_PAUSED'
  | 'LAYERED_TEAM_SYSTEM_PAUSED'
  | 'RESOLVED_LONG_SYSTEM_PAUSE'
  | 'RESOLVED_LAYERED_TEAM_SYSTEM'
  | 'OVERLAPPING_PAUSES_EXPIRED';

export type DeterministicFixtureSeed = {
  type: DeterministicFixtureType;
  roomCode: string;
  gameId: string;
  teams: Array<{ id: string; name: string; buttonCode: string; image: string }>;
  currentScheduleId: string;
  nextScheduleId: string;
  currentTeamInterruptId?: string;
  currentSystemInterruptId?: string;
  resolvedInterruptIds: string[];
};

type InterruptPayload = {
  id: string;
  teamId: string | null;
  arrivedAt: string;
  resolvedAt: string | null;
  correct: boolean | null;
  score: number | null;
  scenario: number | null;
};

type SchedulePayload = {
  id: string;
  trackId: string;
  startedAt: string | null;
  revealedAt: string | null;
  ordinalNumber: number;
  interrupts: InterruptPayload[];
};

function uuid(): string {
  return crypto.randomUUID();
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function localDateTime(offsetMillis = 0): string {
  const date = new Date(Date.now() + offsetMillis);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function roomCode(prefix: string): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  let suffix = '';
  for (let i = 0; i < 3; i++) suffix += alphabet[Math.floor(Math.random() * alphabet.length)];
  return `${prefix}${suffix}`.slice(0, 4);
}

function team(index: number) {
  return {
    id: uuid(),
    name: `E2E Team ${index === 0 ? 'A' : 'B'}`,
    buttonCode: `E2E${index}${Date.now()}`,
    image: `https://example.com/e2e-${index}.png`,
  };
}

function interrupt(args: {
  id?: string;
  teamId: string | null;
  arrivedAt: string;
  resolvedAt: string | null;
  correct?: boolean | null;
  score?: number | null;
  scenario?: number | null;
}): InterruptPayload {
  return {
    id: args.id ?? uuid(),
    teamId: args.teamId,
    arrivedAt: args.arrivedAt,
    resolvedAt: args.resolvedAt,
    correct: args.correct ?? null,
    score: args.score ?? null,
    scenario: args.scenario ?? null,
  };
}

function schedule(
  id: string,
  trackId: string,
  ordinalNumber: number,
  interrupts: InterruptPayload[],
): SchedulePayload {
  return {
    id,
    trackId,
    startedAt: localDateTime(-30_000),
    revealedAt: null,
    ordinalNumber,
    interrupts,
  };
}

function buildInterrupts(type: DeterministicFixtureType, teamAId: string) {
  const resolvedInterruptIds: string[] = [];
  let currentTeamInterruptId: string | undefined;
  let currentSystemInterruptId: string | undefined;

  const startedPlus1 = localDateTime(-29_000);
  const startedPlus2 = localDateTime(-28_000);

  if (type === 'EXPIRED_NO_PAUSE') {
    return {
      interrupts: [],
      currentTeamInterruptId,
      currentSystemInterruptId,
      resolvedInterruptIds,
    };
  }

  if (type === 'TEAM_PAUSED') {
    const id = uuid();
    currentTeamInterruptId = id;
    return {
      interrupts: [
        // Valid ongoing team interrupt: team answered state, no score/correct until resolved.
        interrupt({ id, teamId: teamAId, arrivedAt: startedPlus1, resolvedAt: null }),
      ],
      currentTeamInterruptId,
      currentSystemInterruptId,
      resolvedInterruptIds,
    };
  }

  if (type === 'SYSTEM_PAUSED') {
    const id = uuid();
    currentSystemInterruptId = id;
    return {
      interrupts: [
        // Valid ongoing system interrupt: exactly one unresolved system pause carries scenario.
        interrupt({ id, teamId: null, arrivedAt: startedPlus1, resolvedAt: null, scenario: 4 }),
      ],
      currentTeamInterruptId,
      currentSystemInterruptId,
      resolvedInterruptIds,
    };
  }

  if (type === 'LAYERED_TEAM_SYSTEM_PAUSED') {
    const teamInterrupt = uuid();
    const systemInterrupt = uuid();
    currentTeamInterruptId = teamInterrupt;
    currentSystemInterruptId = systemInterrupt;
    return {
      interrupts: [
        // Valid ordering: ongoing team interrupt arrives before all ongoing system interrupts.
        interrupt({
          id: teamInterrupt,
          teamId: teamAId,
          arrivedAt: startedPlus1,
          resolvedAt: null,
        }),
        // Exactly one ongoing system interrupt has the scenario marker.
        interrupt({
          id: systemInterrupt,
          teamId: null,
          arrivedAt: startedPlus2,
          resolvedAt: null,
          scenario: 4,
        }),
      ],
      currentTeamInterruptId,
      currentSystemInterruptId,
      resolvedInterruptIds,
    };
  }

  if (type === 'RESOLVED_LONG_SYSTEM_PAUSE') {
    const id = uuid();
    resolvedInterruptIds.push(id);
    return {
      interrupts: [
        // Historical system pause: song started 30s ago, pause covered 28s, so effective seek is about 2s.
        interrupt({
          id,
          teamId: null,
          arrivedAt: startedPlus1,
          resolvedAt: localDateTime(-1_000),
          scenario: 4,
        }),
      ],
      currentTeamInterruptId,
      currentSystemInterruptId,
      resolvedInterruptIds,
    };
  }

  if (type === 'RESOLVED_LAYERED_TEAM_SYSTEM') {
    const teamInterrupt = uuid();
    const systemInterrupt = uuid();
    const resolvedAt = localDateTime(-1_000);
    resolvedInterruptIds.push(teamInterrupt, systemInterrupt);
    return {
      interrupts: [
        // Valid resolved layered state: once team answer is resolved, all interrupts resolve at same timestamp.
        interrupt({
          id: teamInterrupt,
          teamId: teamAId,
          arrivedAt: startedPlus1,
          resolvedAt,
          correct: true,
          score: 10,
        }),
        interrupt({
          id: systemInterrupt,
          teamId: null,
          arrivedAt: startedPlus2,
          resolvedAt,
          scenario: 4,
        }),
      ],
      currentTeamInterruptId,
      currentSystemInterruptId,
      resolvedInterruptIds,
    };
  }

  if (type === 'OVERLAPPING_PAUSES_EXPIRED') {
    const outer = uuid();
    const inner = uuid();
    resolvedInterruptIds.push(outer, inner);
    return {
      interrupts: [
        // Most-encompassing interval is now-20..now-5 = 15s.
        // Nested now-18..now-5 must not be double-counted.
        // Effective seek is about 15s, so team buzz should be rejected.
        interrupt({
          id: outer,
          teamId: null,
          arrivedAt: localDateTime(-20_000),
          resolvedAt: localDateTime(-5_000),
          scenario: 4,
        }),
        interrupt({
          id: inner,
          teamId: null,
          arrivedAt: localDateTime(-18_000),
          resolvedAt: localDateTime(-5_000),
          scenario: null,
        }),
      ],
      currentTeamInterruptId,
      currentSystemInterruptId,
      resolvedInterruptIds,
    };
  }

  return { interrupts: [], currentTeamInterruptId, currentSystemInterruptId, resolvedInterruptIds };
}

export function buildDeterministicFixture(type: DeterministicFixtureType): {
  seed: DeterministicFixtureSeed;
  request: unknown;
} {
  const teamA = team(0);
  const teamB = team(1);
  const gameId = uuid();
  const categoryId = uuid();
  const albumId = uuid();
  const currentScheduleId = uuid();
  const nextScheduleId = uuid();
  const currentTrackId = uuid();
  const nextTrackId = uuid();

  const { interrupts, currentTeamInterruptId, currentSystemInterruptId, resolvedInterruptIds } =
    buildInterrupts(type, teamA.id);

  const seed: DeterministicFixtureSeed = {
    type,
    roomCode: roomCode('D'),
    gameId,
    teams: [teamA, teamB],
    currentScheduleId,
    nextScheduleId,
    currentTeamInterruptId,
    currentSystemInterruptId,
    resolvedInterruptIds,
  };

  const request = {
    id: gameId,
    roomCode: seed.roomCode,
    maxSongs: 2,
    maxAlbums: 3,
    stage: 2,
    teams: seed.teams,
    categories: [
      {
        id: categoryId,
        pickedByTeamId: teamA.id,
        ordinalNumber: 1,
        done: false,
        album: {
          id: albumId,
          name: `E2E Deterministic ${type}`,
          customQuestion: 'E2E deterministic question',
          tracks: [
            {
              customAnswer: 'E2E deterministic answer 1',
              schedule: schedule(currentScheduleId, currentTrackId, 1, interrupts),
            },
            {
              customAnswer: 'E2E deterministic answer 2',
              schedule: {
                id: nextScheduleId,
                trackId: nextTrackId,
                startedAt: null,
                revealedAt: null,
                ordinalNumber: 2,
                interrupts: [],
              },
            },
          ],
        },
      },
      {
        id: uuid(),
        pickedByTeamId: null,
        ordinalNumber: null,
        done: false,
        album: {
          id: uuid(),
          name: 'E2E Unchosen Album 2',
          customQuestion: 'E2E unchosen question 2',
          tracks: [
            { customAnswer: 'Unused 2.1', schedule: null },
            { customAnswer: 'Unused 2.2', schedule: null },
          ],
        },
      },
      {
        id: uuid(),
        pickedByTeamId: null,
        ordinalNumber: null,
        done: false,
        album: {
          id: uuid(),
          name: 'E2E Unchosen Album 3',
          customQuestion: 'E2E unchosen question 3',
          tracks: [
            { customAnswer: 'Unused 3.1', schedule: null },
            { customAnswer: 'Unused 3.2', schedule: null },
          ],
        },
      },
    ],
  };

  return { seed, request };
}

export async function createDeterministicFixture(
  request: APIRequestContext,
  type: DeterministicFixtureType,
): Promise<DeterministicFixtureSeed> {
  const fixture = buildDeterministicFixture(type);
  await deleteGameFixture(request, fixture.seed.roomCode);
  const response = await request.post(`${BACKEND_URL}/api/e2e/v1/game-fixtures`, {
    data: fixture.request,
  });
  expect(
    response.ok(),
    `create deterministic ${type} fixture failed: ${response.status()} ${await response.text()}`,
  ).toBeTruthy();
  test
    .info()
    .annotations.push({ type: 'fixture-room', description: `${type}:${fixture.seed.roomCode}` });
  return fixture.seed;
}

export async function withDeterministicFixture<T>(
  request: APIRequestContext,
  type: DeterministicFixtureType,
  fn: (seed: DeterministicFixtureSeed) => Promise<T>,
): Promise<T> {
  const seed = await createDeterministicFixture(request, type);
  try {
    return await fn(seed);
  } finally {
    await deleteGameFixture(request, seed.roomCode);
  }
}
