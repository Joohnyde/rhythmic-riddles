import { Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AudioAssetUrlService {
  snippetUrl(songId: string): string {
    return `${environment.apiUrl}/assets/v1/audio/snippets/${songId}`;
  }

  answerUrl(songId: string): string {
    return `${environment.apiUrl}/assets/v1/audio/answers/${songId}`;
  }
}
