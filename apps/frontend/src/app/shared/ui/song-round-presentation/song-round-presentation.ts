import { Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, catchError, concat, filter, map, of, switchMap, timer } from 'rxjs';

import { GameServerMessage } from '../../../domain/game/messages/game-server-message.types';
import { SongRoundVm } from '../../../domain/game/state/song-round.store';

const ANSWER_FEEDBACK_MS = 2700;

export function createAnswerFeedback(
  messages$: Observable<GameServerMessage> | undefined,
  vm: Signal<SongRoundVm>,
) {
  return toSignal(
    (messages$ ?? EMPTY).pipe(
      filter((message) =>
        message.type === 'answer'
          ? message.scheduleId === vm().lastPlayedSong
          : message.type === 'song_next' ||
            message.type === 'song_repeat' ||
            message.type === 'welcome',
      ),
      switchMap((message) => {
        if (message.type !== 'answer') {
          return of(null);
        }
        const team = vm().teams.find(({ teamId }) => teamId === message.teamId);
        const feedback = { correct: message.correct, teamName: team?.name ?? 'Team' };
        return concat(of(feedback), timer(ANSWER_FEEDBACK_MS).pipe(map(() => null)));
      }),
      catchError(() => of(null)),
    ),
    { initialValue: null },
  );
}
