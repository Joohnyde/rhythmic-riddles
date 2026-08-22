import { describe, expect, it } from 'vitest';
import { Team } from '../../../domain/game/models/team.model';
import {
  TV_MUSIC_LINE_COUNT,
  TV_TEAMS_PER_PAGE,
  clampPage,
  nextPage,
  pageCount,
  pathPointAtFraction,
  teamLinePosition,
  teamsForPage,
} from './tv-lobby-layout';

function teams(count: number): readonly Team[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `team-${index}`,
    name: `Team ${index}`,
    image: `/team-icons/icon_${index.toString(16).padStart(6, '0')}.png`,
  }));
}

describe('TV lobby pagination', () => {
  it('shows the newly added team when the blank state becomes non-empty', () => {
    const state = teams(1);
    expect(pageCount(state.length)).toBe(1);
    expect(teamsForPage(state, 0)).toEqual(state);
  });

  it('returns to a valid blank first page when the only team is removed', () => {
    expect(clampPage(0, 0)).toBe(0);
    expect(teamsForPage([], 0)).toEqual([]);
  });

  it('keeps the current page composition stable when a team is added off-page', () => {
    const before = teams(9);
    const after = teams(10);
    expect(teamsForPage(after, 0)).toEqual(teamsForPage(before, 0));
  });

  it('keeps the current page stable when a team is removed off-page', () => {
    const before = teams(12);
    const after = before.filter((team) => team.id !== 'team-10');
    expect(teamsForPage(after, 0)).toEqual(teamsForPage(before, 0));
  });

  it('fills the current non-full page when a team is added', () => {
    const state = teams(9);
    expect(teamsForPage(state, 1)).toHaveLength(2);
    expect(teamsForPage(teams(10), 1)).toHaveLength(3);
  });

  it('fills a removed slot on a non-full page when a later team is available', () => {
    const state = teams(16);
    const removed = state.filter((team) => team.id !== 'team-8');
    expect(teamsForPage(removed, 1).map((team) => team.id)).toEqual([
      'team-7',
      'team-9',
      'team-10',
      'team-11',
      'team-12',
      'team-13',
      'team-14',
    ]);
  });

  it('leaves a non-full page non-full when no later team can fill the gap', () => {
    const state = teams(10).filter((team) => team.id !== 'team-8');
    expect(teamsForPage(state, 1).map((team) => team.id)).toEqual(['team-7', 'team-9']);
  });

  it('creates a second page when a team is added to a full page', () => {
    expect(pageCount(TV_TEAMS_PER_PAGE)).toBe(1);
    expect(pageCount(TV_TEAMS_PER_PAGE + 1)).toBe(2);
    expect(teamsForPage(teams(8), 1).map((team) => team.id)).toEqual(['team-7']);
  });

  it('moves to the previous valid page when the only team on the current page is removed', () => {
    expect(clampPage(1, 7)).toBe(0);
    expect(teamsForPage(teams(7), 1)).toHaveLength(7);
  });

  it('recovers from invalid negative and oversized page indexes', () => {
    expect(clampPage(-3, 15)).toBe(0);
    expect(clampPage(99, 15)).toBe(2);
  });

  it('wraps page advancement after the last page and handles shrinking page counts', () => {
    expect(nextPage(0, 15)).toBe(1);
    expect(nextPage(1, 15)).toBe(2);
    expect(nextPage(2, 15)).toBe(0);
    expect(nextPage(2, 7)).toBe(0);
  });

  it('remains valid through rapid page-count growth and shrinkage', () => {
    const counts = [0, 1, 7, 8, 15, 14, 7, 0];
    let page = 0;
    for (const count of counts) {
      page = clampPage(page, count);
      expect(page).toBeGreaterThanOrEqual(0);
      expect(page).toBeLessThan(Math.max(pageCount(count), 1));
      page = nextPage(page, count);
    }
  });

  it('preserves stable ordering across exact page-size boundaries', () => {
    const state = teams(14);
    expect(teamsForPage(state, 0).map((team) => team.id)).toEqual(
      Array.from({ length: 7 }, (_, index) => `team-${index}`),
    );
    expect(teamsForPage(state, 1).map((team) => team.id)).toEqual(
      Array.from({ length: 7 }, (_, index) => `team-${index + 7}`),
    );
  });
});

describe('TV team line layout', () => {
  it('assigns every page slot to a bounded point somewhere along a music line', () => {
    teams(TV_TEAMS_PER_PAGE).forEach((team, index) => {
      const position = teamLinePosition(team, index);
      expect(position.lineIndex).toBeGreaterThanOrEqual(0);
      expect(position.lineIndex).toBeLessThan(5);
      expect(position.fraction).toBeGreaterThanOrEqual(0.04);
      expect(position.fraction).toBeLessThanOrEqual(0.96);
    });
  });

  it('keeps a team on the same deterministic line and fraction across redraws', () => {
    const team = teams(1)[0];
    expect(teamLinePosition(team, 3)).toEqual(teamLinePosition(team, 3));
  });

  it('spreads a full page from near the start to near the end of the line paths', () => {
    const fractions = teams(TV_TEAMS_PER_PAGE).map(
      (team, index) => teamLinePosition(team, index).fraction,
    );

    expect(fractions[0]).toBeLessThanOrEqual(0.1);
    expect(fractions.at(-1)).toBeGreaterThanOrEqual(0.9);
    for (let index = 1; index < fractions.length; index += 1) {
      expect(fractions[index]).toBeGreaterThan(fractions[index - 1]);
    }
  });

  it('can distribute teams across every configured music line', () => {
    const usedLines = new Set(
      teams(200).map((team, index) => teamLinePosition(team, index).lineIndex),
    );

    expect([...usedLines].sort((left, right) => left - right)).toEqual(
      Array.from({ length: TV_MUSIC_LINE_COUNT }, (_, index) => index),
    );
  });

  it('projects points across the full path length, including both endpoints', () => {
    const sampled: number[] = [];
    const path = {
      getTotalLength: () => 1000,
      getPointAtLength: (distance: number) => {
        sampled.push(distance);
        return { x: distance, y: 0 } as DOMPoint;
      },
    };

    for (const fraction of [0, 0.25, 0.5, 0.75, 1]) {
      pathPointAtFraction(path, fraction);
    }

    expect(sampled).toEqual([0, 250, 500, 750, 1000]);
  });

  it('clamps path projections that fall outside the line', () => {
    const sampled: number[] = [];
    const path = {
      getTotalLength: () => 100,
      getPointAtLength: (distance: number) => {
        sampled.push(distance);
        return { x: distance, y: 0 } as DOMPoint;
      },
    };

    pathPointAtFraction(path, -1);
    pathPointAtFraction(path, 2);
    expect(sampled).toEqual([0, 100]);
  });
});
