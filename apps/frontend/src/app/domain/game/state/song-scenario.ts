export enum SongScenario {
  Loading = -1,
  Revealed = 0,
  FinishedUnrevealed = 1,
  TeamAnswering = 2,
  SystemError = 3,
  Playing = 4,
}

const SONG_SCENARIO_VALUES = new Set<number>([
  SongScenario.Loading,
  SongScenario.Revealed,
  SongScenario.FinishedUnrevealed,
  SongScenario.TeamAnswering,
  SongScenario.SystemError,
  SongScenario.Playing,
]);

export function coerceSongScenario(value: number): SongScenario {
  return SONG_SCENARIO_VALUES.has(value) ? (value as SongScenario) : SongScenario.Loading;
}
