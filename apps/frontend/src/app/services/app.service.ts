import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { EMPTY, Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
    providedIn: 'root',
})
export class AppService {

    constructor(private http: HttpClient) { }

    killApp(): Observable<void> {
        const url = environment.shutdownUrl;
        if (!url) return EMPTY;
        return this.http.post<void>(url, null);
    }
}
