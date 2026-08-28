import { APIRequestContext, expect, test } from '@playwright/test';
import { BACKEND_URL } from './env';

export type FixtureStage = 'LOBBY' | 'ALBUMS' | 'SONGS_LISTENING' | 'SONGS_REVEALED' | 'WINNER';

export type FixtureBuildOptions = {
  roomPrefix?: string;
  categoryCount?: number;
  maxAlbums?: number;
  /** Optional deterministic album names, indexed by category, for order/recovery UI tests. */
  categoryNames?: readonly string[];
  /**
   * Makes backend UUID wire order deliberately differ from frontend canonical name order. The
   * backend remains authoritative for membership; this exists only to prove the browser does not
   * accidentally treat transport order as visual order.
   */
  forceNonCanonicalBackendAlbumOrder?: boolean;
  /** Offset from local now for the active/current song startedAt. Keep near 0 for legal team buzzes. */
  activeStartedOffsetMillis?: number;
  /** Offset from local now for revealedAt in revealed fixtures. */
  revealedOffsetMillis?: number;
};

export type E2eFixtureSeed = {
  type: FixtureStage;
  roomCode: string;
  gameId: string;
  teams: Array<{ id: string; name: string; buttonCode: string; image: string }>;
  categories: Array<{ id: string; albumId: string; name: string; scheduleIds: string[] }>;
  currentScheduleId?: string;
  nextScheduleId?: string;
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

type TrackPayload = {
  customAnswer: string;
  schedule: SchedulePayload | null;
};

type CategoryPayload = {
  id: string;
  pickedByTeamId: string | null;
  ordinalNumber: number | null;
  done: boolean;
  album: {
    id: string;
    name: string;
    customQuestion: string;
    tracks: TrackPayload[];
  };
};

function uuid(): string {
  return crypto.randomUUID();
}

function roomCode(prefix: string): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  let suffix = '';
  for (let i = 0; i < 3; i++) suffix += alphabet[Math.floor(Math.random() * alphabet.length)];
  return `${prefix}${suffix}`.slice(0, 4);
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function localDateTime(offsetMillis = 0): string {
  // Backend DTO uses Java LocalDateTime, which has no timezone.
  // Do not use toISOString(), because it converts to UTC and then drops the Z.
  // Format the Node process local time so Playwright and the backend agree on seek calculations.
  const date = new Date(Date.now() + offsetMillis);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function fixtureTimes(options: FixtureBuildOptions = {}) {
  // The E2E song duration is 9.6s. Team buzz-in is only legal while the effective
  // seek is still inside that window. Do NOT seed the current listening schedule
  // too far in the past, otherwise the backend correctly returns 409 because the
  // snippet has already finished and Admin must choose the next action instead.
  // A tiny negative offset makes the song already started, while leaving almost
  // the full 9.6s window for Admin/TV connection and the test's buzz request.
  const startedAt = localDateTime(options.activeStartedOffsetMillis ?? -500);
  const startedAt2 = localDateTime(60_000);
  const revealedAt = localDateTime(options.revealedOffsetMillis ?? -250);
  const revealedAt2 = localDateTime(62_000);
  return { startedAt, startedAt2, revealedAt, revealedAt2 };
}

function stageOf(type: FixtureStage): number {
  if (type === 'LOBBY') return 0;
  if (type === 'ALBUMS') return 1;
  if (type === 'WINNER') return 3;
  return 2;
}

function prefixOf(type: FixtureStage): string {
  return { LOBBY: 'L', ALBUMS: 'A', SONGS_LISTENING: 'S', SONGS_REVEALED: 'R', WINNER: 'W' }[type];
}

function buildSchedule(
  scheduleId: string,
  trackId: string,
  ordinalNumber: number,
  startedAt: string | null,
  revealedAt: string | null,
): SchedulePayload {
  return { id: scheduleId, trackId, startedAt, revealedAt, ordinalNumber, interrupts: [] };
}

function buildTrack(
  categoryIndex: number,
  trackIndex: number,
  schedule: SchedulePayload | null,
): TrackPayload {
  return {
    customAnswer: `E2E Answer ${categoryIndex + 1}.${trackIndex + 1}`,
    schedule,
  };
}

type E2eFixtureRequest = {
  stage?: number;
  maxSongs?: number;
  maxAlbums?: number;
  categories?: Array<{
    ordinalNumber?: number | null;
    album?: {
      tracks?: Array<{
        schedule?: {
          startedAt?: string | null;
          revealedAt?: string | null;
          interrupts?: Array<{
            teamId?: string | null;
            arrivedAt?: string | null;
            resolvedAt?: string | null;
            scenario?: number | null;
          }>;
        } | null;
      }>;
    };
  }>;
};

export function assertReachableFixtureState(request: E2eFixtureRequest): void {
  const categories = request.categories as CategoryPayload[];
  const allSchedules = categories.flatMap((category) =>
    category.album.tracks.map((track) => track.schedule).filter(Boolean),
  ) as SchedulePayload[];
  const startedSchedules = allSchedules.filter((schedule) => schedule.startedAt !== null);
  const activeSchedules = allSchedules.filter(
    (schedule) => schedule.startedAt !== null && schedule.revealedAt === null,
  );

  // Stage 2 can be either listening (one active started/unrevealed schedule) or revealed (started+revealed current schedule).
  // The real invariant is: at least one started schedule, and at most one started-but-unrevealed schedule.
  if (request.stage === 2) {
    if (startedSchedules.length < 1)
      throw new Error('Stage 2 fixture must have at least one started schedule.');
    if (activeSchedules.length > 1)
      throw new Error(
        `Stage 2 fixture can have at most one active schedule; got ${activeSchedules.length}`,
      );
  }

  if (request.stage !== 2 && activeSchedules.length !== 0) {
    throw new Error(
      `Only Stage 2 fixtures may have an active schedule; got ${activeSchedules.length}`,
    );
  }

  for (const category of categories) {
    const schedules = category.album.tracks
      .map((track) => track.schedule)
      .filter(Boolean) as SchedulePayload[];

    if (category.ordinalNumber === null) {
      if (category.pickedByTeamId !== null)
        throw new Error('Unchosen category must not have pickedByTeamId.');
      if (category.done) throw new Error('Unchosen category must not be done.');
      if (schedules.length > 0) throw new Error('Unchosen category must not have schedules.');
      continue;
    }

    if (category.pickedByTeamId === null)
      throw new Error('Chosen category must have pickedByTeamId.');
    if (schedules.length !== request.maxSongs)
      throw new Error('Chosen category must have one schedule per selected song.');

    const scheduleOrdinals = schedules
      .map((schedule) => schedule.ordinalNumber)
      .sort((a, b) => a - b);
    expect(scheduleOrdinals).toEqual(
      Array.from({ length: request.maxSongs }, (_, index) => index + 1),
    );

    if (category.done) {
      for (const schedule of schedules) {
        if (schedule.startedAt === null || schedule.revealedAt === null) {
          throw new Error('Done category must have every scheduled song started and revealed.');
        }
      }
    }

    for (const schedule of schedules) {
      const interrupts = schedule.interrupts ?? [];
      for (const interrupt of interrupts) {
        if (!interrupt.arrivedAt) throw new Error('Every interrupt must have arrivedAt.');
        if (interrupt.teamId === null && interrupt.score !== null)
          throw new Error('System interrupt must not have score.');
        if (interrupt.teamId !== null && interrupt.scenario !== null)
          throw new Error('Team interrupt must not have scenario.');
      }

      const ongoingSystem = interrupts.filter(
        (interrupt) => interrupt.teamId === null && interrupt.resolvedAt === null,
      );
      const scenarioInterrupts = ongoingSystem.filter(
        (interrupt) =>
          interrupt.scenario !== null &&
          interrupt.scenario >= 0 &&
          interrupt.scenario <= 4 &&
          interrupt.scenario !== 3,
      );
      if (ongoingSystem.length > 0 && scenarioInterrupts.length !== 1) {
        throw new Error(
          'Ongoing system interrupts must contain exactly one crash scenario marker.',
        );
      }

      const ongoingTeam = interrupts.filter(
        (interrupt) => interrupt.teamId !== null && interrupt.resolvedAt === null,
      );
      if (ongoingTeam.length > 1)
        throw new Error('There can be at most one ongoing team interrupt.');
      if (ongoingTeam.length === 1) {
        const teamArrivedAt = new Date(ongoingTeam[0].arrivedAt).getTime();
        for (const systemInterrupt of ongoingSystem) {
          if (teamArrivedAt >= new Date(systemInterrupt.arrivedAt).getTime()) {
            throw new Error(
              'Ongoing team interrupt must arrive before all ongoing system interrupts.',
            );
          }
        }
      }
    }
  }
}

function buildReverseCanonicalNameById(
  categoryIds: readonly string[],
  sourceNames: readonly string[],
): Map<string, string> {
  const idsInBackendOrder = [...categoryIds].sort(compareFixtureText);
  const namesInCanonicalOrder = [...sourceNames].sort((left, right) =>
    compareFixtureText(left.normalize('NFKD').toLowerCase(), right.normalize('NFKD').toLowerCase()),
  );
  return new Map(
    idsInBackendOrder.map((id, index) => [
      id,
      namesInCanonicalOrder[namesInCanonicalOrder.length - 1 - index]!,
    ]),
  );
}

function compareFixtureText(left: string, right: string): number {
  const maxIndex = Math.min(left.length, right.length);
  for (let index = 0; index < maxIndex; index += 1) {
    const difference = left.charCodeAt(index) - right.charCodeAt(index);
    if (difference !== 0) return difference;
  }
  return left.length - right.length;
}

export function buildGameFixture(
  type: FixtureStage,
  options: FixtureBuildOptions = {},
): { seed: E2eFixtureSeed; request: unknown } {
  const code = roomCode(options.roomPrefix ?? prefixOf(type));
  const teamA = {
    id: uuid(),
    name: 'E2E Team A',
    buttonCode: `E2EA-${Date.now()}`,
    image: 'https://example.com/e2e-a.png',
  };
  const teamB = {
    id: uuid(),
    name: 'E2E Team B',
    buttonCode: `E2EB-${Date.now()}`,
    image: 'https://example.com/e2e-b.png',
  };
  const stage = stageOf(type);
  const times = fixtureTimes(options);
  const categoryCount = options.categoryCount ?? 3;
  const maxAlbums = options.maxAlbums ?? Math.min(3, categoryCount);

  const categoryIds = Array.from({ length: categoryCount }, () => uuid());
  const sourceNames = Array.from(
    { length: categoryCount },
    (_, index) => options.categoryNames?.[index] ?? `E2E Album ${index + 1}`,
  );
  const forcedNameById = options.forceNonCanonicalBackendAlbumOrder
    ? buildReverseCanonicalNameById(categoryIds, sourceNames)
    : null;
  const categories: Array<{ id: string; albumId: string; name: string; scheduleIds: string[] }> =
    [];

  const categoryPayloads: CategoryPayload[] = Array.from(
    { length: categoryCount },
    (_, categoryIndex) => categoryIndex,
  ).map((categoryIndex) => {
    const categoryId = categoryIds[categoryIndex]!;
    const albumId = uuid();
    const trackIds = [uuid(), uuid()];
    const scheduleIds = [uuid(), uuid()];

    const isWinner = type === 'WINNER';
    const isCurrentSongsCategory =
      (type === 'SONGS_LISTENING' || type === 'SONGS_REVEALED') && categoryIndex === 0;
    const isChosen = isWinner || isCurrentSongsCategory;
    const isDone = isWinner;

    const schedules: Array<SchedulePayload | null> = [null, null];

    if (isWinner) {
      schedules[0] = buildSchedule(
        scheduleIds[0],
        trackIds[0],
        1,
        times.startedAt,
        times.revealedAt,
      );
      schedules[1] = buildSchedule(
        scheduleIds[1],
        trackIds[1],
        2,
        times.startedAt2,
        times.revealedAt2,
      );
    } else if (isCurrentSongsCategory) {
      schedules[0] = buildSchedule(
        scheduleIds[0],
        trackIds[0],
        1,
        times.startedAt,
        type === 'SONGS_REVEALED' ? times.revealedAt : null,
      );
      schedules[1] = buildSchedule(scheduleIds[1], trackIds[1], 2, null, null);
    }

    const albumName = forcedNameById?.get(categoryId) ?? sourceNames[categoryIndex]!;
    categories.push({
      id: categoryId,
      albumId,
      name: albumName,
      scheduleIds: isChosen ? scheduleIds : [],
    });

    return {
      id: categoryId,
      pickedByTeamId: isChosen ? teamA.id : null,
      ordinalNumber: isChosen ? categoryIndex + 1 : null,
      done: isDone,
      album: {
        id: albumId,
        name: albumName,
        customQuestion: `E2E Album Question ${categoryIndex + 1}`,
        tracks: [
          buildTrack(categoryIndex, 0, schedules[0]),
          buildTrack(categoryIndex, 1, schedules[1]),
        ],
      },
    };
  });

  const currentCategory = categories[0];
  const seed: E2eFixtureSeed = {
    type,
    roomCode: code,
    gameId: uuid(),
    teams: [teamA, teamB],
    categories,
    currentScheduleId: stage === 2 ? currentCategory.scheduleIds[0] : undefined,
    nextScheduleId: stage === 2 ? currentCategory.scheduleIds[1] : undefined,
  };

  const request = {
    id: seed.gameId,
    roomCode: seed.roomCode,
    maxSongs: 2,
    maxAlbums,
    stage,
    teams: seed.teams,
    categories: categoryPayloads,
  };
  assertReachableFixtureState(request);
  return { seed, request };
}

export async function deleteGameFixture(
  request: APIRequestContext,
  roomCode: string,
): Promise<void> {
  await request
    .delete(`${BACKEND_URL}/api/e2e/v1/game-fixtures/${roomCode}`)
    .catch(() => undefined);
}

export async function createGameFixture(
  request: APIRequestContext,
  type: FixtureStage,
  options: FixtureBuildOptions = {},
): Promise<E2eFixtureSeed> {
  const fixture = buildGameFixture(type, options);
  await deleteGameFixture(request, fixture.seed.roomCode);
  const response = await request.post(`${BACKEND_URL}/api/e2e/v1/game-fixtures`, {
    data: fixture.request,
  });
  expect(
    response.ok(),
    `create ${type} fixture failed: ${response.status()} ${await response.text()}`,
  ).toBeTruthy();
  test
    .info()
    .annotations.push({ type: 'fixture-room', description: `${type}:${fixture.seed.roomCode}` });
  return fixture.seed;
}

export async function withGameFixture<T>(
  request: APIRequestContext,
  type: FixtureStage,
  optionsOrFn: FixtureBuildOptions | ((seed: E2eFixtureSeed) => Promise<T>),
  maybeFn?: (seed: E2eFixtureSeed) => Promise<T>,
): Promise<T> {
  const options = typeof optionsOrFn === 'function' ? {} : optionsOrFn;
  const fn = typeof optionsOrFn === 'function' ? optionsOrFn : maybeFn;

  if (!fn) {
    throw new Error('withGameFixture requires a callback');
  }

  const seed = await createGameFixture(request, type, options);
  try {
    return await fn(seed);
  } finally {
    await deleteGameFixture(request, seed.roomCode);
  }
}
