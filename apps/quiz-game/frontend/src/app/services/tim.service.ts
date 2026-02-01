import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Storage } from '../utils/storage';

export interface CreateTimRequest {
  ime: string;
  slika: string;
  dugme: string; // UUID
}

@Injectable({
  providedIn: 'root',
})
export class TimService {
   private apiUrl = 'http://localhost:8080/tim';  // <-- adjust if needed

  constructor(private http: HttpClient, private storage: Storage) {}

  prepareHeaders(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
     const headers = new HttpHeaders({
      'ROOM_CODE': this.storage.code
    });
    return headers;
  }

  createTeam(req: CreateTimRequest): Observable<any> {
    return this.http.post(this.apiUrl, req, {headers: this.prepareHeaders()} );
  }
  kickTeam(id : string): Observable<any> {
    return this.http.delete(this.apiUrl+"?tim_id="+id, {headers: this.prepareHeaders()} );
  }
}
