import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../../environments/environment';
import { SongRoundAlbumComponent } from './song-round-album.component';

const album = {
  image: 'album-one',
  name: 'Rock classics',
  picker: 'team.png',
  pickerName: 'Team Sun',
};

describe('SongRoundAlbumComponent', () => {
  let fixture: ComponentFixture<SongRoundAlbumComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SongRoundAlbumComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(SongRoundAlbumComponent);
    fixture.componentRef.setInput('album', album);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('renders the server album and selecting team without browser storage', () => {
    const host = fixture.nativeElement as HTMLElement;
    const cover = host.querySelector<HTMLImageElement>('.album-image')!;
    expect(cover.getAttribute('src')).toBe(
      `${environment.apiUrl}/assets/v1/image/albums/album-one`,
    );
    expect(cover.alt).toBe(album.name);
    expect(host.querySelector('.album-name')?.textContent).toContain(album.name);
    expect(host.querySelector('.album-picked-by')?.textContent).toContain('Team Sun');
    expect(host.querySelector('.album-picker')?.getAttribute('src')).toBe('team.png');
    expect(host.querySelector('.album-picker')?.getAttribute('alt')).toContain('Team Sun');
  });

  it('handles Admin picks and resets a failed cover when the album changes', () => {
    const host = fixture.nativeElement as HTMLElement;
    host.querySelector('.album-image')!.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(host.querySelector('.album-placeholder')).not.toBeNull();
    expect(host.querySelector('.album-picker')).not.toBeNull();

    fixture.componentRef.setInput('album', {
      image: 'album-two',
      name: 'Jazz',
      picker: null,
      pickerName: 'Admin',
    });
    fixture.detectChanges();
    expect(host.querySelector('.album-placeholder')).toBeNull();
    expect(host.querySelector('.album-image')?.getAttribute('alt')).toBe('Jazz');
    expect(host.querySelector('.album-picker')).toBeNull();
    expect(host.querySelector('.album-picked-by')?.textContent).toContain('Admin');
  });
});
