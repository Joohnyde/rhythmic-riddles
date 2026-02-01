import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class IgraService {
  private apiUrl = 'http://localhost:8080/igra';  // <-- adjust if needed

  constructor(private http: HttpClient, private storage: Storage) {}

  prepareHeaders(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
     const headers = new HttpHeaders({
      'ROOM_CODE': this.storage.code
    });
    return headers;
  }
  
  changeState(id: number): Observable<any> {
    return this.http.post(this.apiUrl+"/changeState?stage_id="+id, null, {headers: this.prepareHeaders()} );
  }
}
