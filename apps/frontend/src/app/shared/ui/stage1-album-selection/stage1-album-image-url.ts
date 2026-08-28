import { environment } from '../../../../environments/environment';

export function getStage1AlbumImageUrl(image: string): string {
  const fileName = image.split('/').pop() ?? image;
  const albumId = fileName.replace(/\.[^.]+$/, '');
  return `${environment.apiUrl}/assets/v1/image/albums/${albumId}`;
}
