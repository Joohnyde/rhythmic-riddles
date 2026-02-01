import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class OdgovorService {
  private apiUrl = 'http://localhost:8080/odgovor';  // <-- adjust if needed

  constructor(private http: HttpClient, private storage: Storage) {}

  prepareHeaders(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
     const headers = new HttpHeaders({
      'ROOM_CODE': this.storage.code
    });
    return headers;
  }
  
  resolve_error(zadnji: string): Observable<any> {
    return this.http.put(this.apiUrl+"/continue?zadnji="+zadnji, null, {headers: this.prepareHeaders()} );
  }

  answer(odgovor_Id: string, correct: boolean): Observable<any> {
    return this.http.post(this.apiUrl+"/answer", {"odgovor_id":odgovor_Id, "correct":correct}, {headers: this.prepareHeaders()} );
  }

  savePrevScenario(prev_scenario: number): Observable<unknown> {
    return this.http.put(this.apiUrl+"/previous_scenario?scenario="+prev_scenario, null, {headers: this.prepareHeaders()} )
  }

}
