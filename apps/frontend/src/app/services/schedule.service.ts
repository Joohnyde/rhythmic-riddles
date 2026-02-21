import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ScheduleService {
  
  constructor(private http: HttpClient, private storage: Storage) {}

  apiUrl(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
    return `${environment.apiUrl}/api/v1/games/${this.storage.code}/schedules`;
  }

  revealAnswer(scheduleId: string): Observable<any> {
    return this.http.post(this.apiUrl()+"/"+scheduleId+"/reveal", null);
  }
  
  replaySong(scheduleId: string): Observable<any> {
    return this.http.post(this.apiUrl()+"/"+scheduleId+"/replay", null);
  }
  
  next(): Observable<any> {
    return this.http.post(this.apiUrl()+"/next", null );
  }
}
