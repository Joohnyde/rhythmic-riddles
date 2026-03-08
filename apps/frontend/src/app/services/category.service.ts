import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Storage } from '../utils/storage';
import { Observable } from 'rxjs';
import { LastCategory } from '../entities/selected.album';

@Injectable({
  providedIn: 'root',
})
export class CategoryService {
  constructor(
    private http: HttpClient,
    private storage: Storage,
  ) {}

  apiUrl(categoryId: string) {
    if (this.storage.code == '') throw new Error('Unknown room_code');
    return 'http://localhost:8080/api/v1/games/' + this.storage.code + '/categories/' + categoryId;
  }

  pickAlbum(categoryId: string, teamId: string | null): Observable<LastCategory> {
    return this.http.put<LastCategory>(this.apiUrl(categoryId) + '/pick', { teamId: teamId });
  }

  start(categoryId: string): Observable<void> {
    return this.http.post<void>(this.apiUrl(categoryId) + '/start', null);
  }
}
