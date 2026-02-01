import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class KategorijaService {
  private apiUrl = 'http://localhost:8080/kategorija';  // <-- adjust if needed

  constructor(private http: HttpClient, private storage: Storage) {}

  prepareHeaders(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
     const headers = new HttpHeaders({
      'ROOM_CODE': this.storage.code
    });
    return headers;
  }
  
  pick(kategorija_id : string, tim_id : string | null): Observable<any> {
    return this.http.post(this.apiUrl+"/pick", {'kategorija_id':kategorija_id, 'tim_id':tim_id}, {headers: this.prepareHeaders()} );
  }

  start(kategorija_id : string): Observable<any> {
    return this.http.post(this.apiUrl+"/start?kategorija="+kategorija_id, null, {headers: this.prepareHeaders()} );
  }
}
