import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class Storage {
  public messageObservable !: Observable<any>;
  public code : string = "";  
}
