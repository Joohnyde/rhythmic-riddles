import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class GameService {

  constructor(private http: HttpClient, private storage: Storage) {}

  apiUrl(){
    if(this.storage.code == "") throw new Error("Unknown room_code");
    return 'http://localhost:8080/api/v1/games/'+this.storage.code+'';
  }
  
  changeState(id: number): Observable<any> {
    return this.http.put(this.apiUrl()+"/stage", {"stageId":id} );
  }
}
