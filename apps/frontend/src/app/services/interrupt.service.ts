import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class InterruptService {
  constructor(private http: HttpClient, private storage: Storage) {}  
  
  apiUrl(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
    return `${environment.apiUrl}/api/v1/games/${this.storage.code}`;
  }

  resolveErrors(scheduleId: string): Observable<any> {
    return this.http.post(this.apiUrl()+"/interrupts/system/resolve", {"scheduleId" : scheduleId});
  }

  answer(answerId: string, correct: boolean): Observable<any> {
    return this.http.post(this.apiUrl()+"/interrupts/"+answerId+"/answer", {"correct":correct});
  }

  savePrevScenario(previousScenario: number): Observable<unknown> {
    return this.http.put(this.apiUrl()+"/ui/scenario", {"scenario" : previousScenario})
  }

}
