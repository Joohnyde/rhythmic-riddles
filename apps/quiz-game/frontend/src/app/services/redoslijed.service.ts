import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class RedoslijedService {
  private apiUrl = 'http://localhost:8080/redoslijed';  // <-- adjust if needed

  constructor(private http: HttpClient, private storage: Storage) {}

  prepareHeaders(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
     const headers = new HttpHeaders({
      'ROOM_CODE': this.storage.code
    });
    return headers;
  }
  
  reveal(zadnji: string): Observable<any> {
    return this.http.post(this.apiUrl+"/reveal?zadnji="+zadnji, null, {headers: this.prepareHeaders()} );
  }
  
  refresh(zadnji: string): Observable<any> {
    return this.http.post(this.apiUrl+"/refresh?zadnji="+zadnji, null, {headers: this.prepareHeaders()} );
  }
  
  next(): Observable<any> {
    return this.http.post(this.apiUrl+"/next", null, {headers: this.prepareHeaders()} );
  }
}
