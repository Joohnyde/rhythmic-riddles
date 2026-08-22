import { Team } from '../../../domain/game/models/team.model';

export const TV_TEAMS_PER_PAGE = 7;
export const TV_MUSIC_LINE_COUNT = 5;

const FIRST_TEAM_FRACTION = 0.08;
const LAST_TEAM_FRACTION = 0.92;

export interface TeamLinePosition {
  readonly lineIndex: number;
  readonly fraction: number;
}

export function pageCount(teamCount: number, pageSize = TV_TEAMS_PER_PAGE): number {
  if (teamCount <= 0) {
    return 0;
  }
  return Math.ceil(teamCount / pageSize);
}

export function clampPage(page: number, teamCount: number, pageSize = TV_TEAMS_PER_PAGE): number {
  const pages = pageCount(teamCount, pageSize);
  if (pages === 0) {
    return 0;
  }
  return Math.min(Math.max(page, 0), pages - 1);
}

export function teamsForPage<T>(
  teams: readonly T[],
  page: number,
  pageSize = TV_TEAMS_PER_PAGE,
): readonly T[] {
  const validPage = clampPage(page, teams.length, pageSize);
  const start = validPage * pageSize;
  return teams.slice(start, start + pageSize);
}

export function nextPage(page: number, teamCount: number, pageSize = TV_TEAMS_PER_PAGE): number {
  const pages = pageCount(teamCount, pageSize);
  return pages <= 1 ? 0 : (clampPage(page, teamCount, pageSize) + 1) % pages;
}

/**
 * Returns a stable line assignment for a team. The page slot controls horizontal placement while
 * the team id only chooses the music line and a small, deterministic offset.
 */
export function teamLinePosition(team: Team, teamIndex: number): TeamLinePosition {
  const slot = Math.abs(teamIndex) % TV_TEAMS_PER_PAGE;
  const slotFraction =
    FIRST_TEAM_FRACTION +
    (slot / (TV_TEAMS_PER_PAGE - 1)) * (LAST_TEAM_FRACTION - FIRST_TEAM_FRACTION);
  const hash = stableHash(team.id);
  const jitter = (((hash >>> 8) % 1001) / 1000 - 0.5) * 0.02;

  return {
    lineIndex: hash % TV_MUSIC_LINE_COUNT,
    fraction: clamp(slotFraction + jitter, 0.04, 0.96),
  };
}

export function pathPointAtFraction(
  path: Pick<SVGPathElement, 'getTotalLength' | 'getPointAtLength'>,
  fraction: number,
): DOMPoint {
  const boundedFraction = clamp(fraction, 0, 1);
  return path.getPointAtLength(path.getTotalLength() * boundedFraction);
}

function stableHash(value: string): number {
  let hash = 2166136261;
  for (const character of value) {
    hash ^= character.codePointAt(0) ?? 0;
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}
