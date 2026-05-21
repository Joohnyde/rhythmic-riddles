export enum SongScenario {
  Loading = -1,
  Revealed = 0,
  FinishedUnrevealed = 1,
  TeamAnswering = 2,
  SystemError = 3,
  Playing = 4,
}
export function coerceSongScenario(value: number): SongScenario {
  return Object.values(SongScenario).includes(value as SongScenario)
    ? (value as SongScenario)
    : SongScenario.Loading;
}
