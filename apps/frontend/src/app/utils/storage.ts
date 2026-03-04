import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DefaultMessage } from '../entities/messages/default.messages';

@Injectable({
  providedIn: 'root',
})
export class Storage {
  public messageObservable!: Observable<DefaultMessage>;
  public code: string = '';
}
