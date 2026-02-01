import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class CategoryService {

  constructor(private http: HttpClient, private storage: Storage) {}
  
  apiUrl(categoryId : string){
    if(this.storage.code == "") throw new Error("Unknown room_code");
    return 'http://localhost:8080/api/v1/games/'+this.storage.code+'/categories/'+categoryId;
  }

  pickAlbum(categoryId : string, teamId : string | null): Observable<any> {
    return this.http.put(this.apiUrl(categoryId)+"/pick", {'teamId':teamId});
  }

  start(categoryId : string): Observable<any> {
    return this.http.post(this.apiUrl(categoryId)+"/start", null);
  }
}
