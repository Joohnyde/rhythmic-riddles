import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlbumCardVm } from '../../../../domain/game/models/album.model';
import { Stage1AlbumCardComponent } from './stage1-album-card.component';

function album(overrides: Partial<AlbumCardVm> = {}): AlbumCardVm {
  return {
    id: 'album-a',
    name: 'YU Rock',
    image: 'yu-rock',
    pickedByTeam: null,
    ordinalNumber: null,
    ...overrides,
  };
}

describe('Stage1AlbumCardComponent', () => {
  let fixture: ComponentFixture<Stage1AlbumCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Stage1AlbumCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(Stage1AlbumCardComponent);
    fixture.componentRef.setInput('album', album());
    fixture.componentRef.setInput('imageUrl', '/albums/yu-rock');
    fixture.componentRef.setInput('testId', 'album-card-album-a');
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('renders a stable album id hook and deterministic gradient-frame variant', () => {
    const card: HTMLElement = fixture.nativeElement.querySelector('.stage1-album-card');
    const firstVariant = card.getAttribute('data-frame-variant');

    fixture.detectChanges();

    expect(card.dataset['albumId']).toBe('album-a');
    expect(card.getAttribute('data-testid')).toBe('album-card-album-a');
    expect(card.getAttribute('data-frame-variant')).toBe(firstVariant);
  });

  it('keeps album art decorative because the visible heading names the card', () => {
    const image: HTMLImageElement = fixture.nativeElement.querySelector('.stage1-album-art');

    expect(image.getAttribute('alt')).toBe('');
    expect(image.getAttribute('aria-hidden')).toBe('true');
  });

  it('emits clicks for interactive available albums', () => {
    const clicked = vi.fn();
    fixture.componentInstance.clickCard.subscribe(clicked);
    fixture.componentRef.setInput('interactive', true);
    fixture.componentRef.setInput('disabled', false);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    button.click();

    expect(button.disabled).toBe(false);
    expect(clicked).toHaveBeenCalledOnce();
  });

  it('disables picked albums and preserves the picker icon', () => {
    fixture.componentRef.setInput(
      'album',
      album({ pickedByTeam: '/team-icons/picker.png', ordinalNumber: 2 }),
    );
    fixture.componentRef.setInput('interactive', true);
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    const icon: HTMLImageElement = fixture.nativeElement.querySelector('.stage1-album-team-icon');

    expect(button.disabled).toBe(true);
    expect(icon.getAttribute('src')).toBe('/team-icons/picker.png');
  });

  it('marks selected and glow states without changing the album identity', () => {
    fixture.componentRef.setInput('selected', true);
    fixture.componentRef.setInput('glow', true);
    fixture.detectChanges();

    const card: HTMLElement = fixture.nativeElement.querySelector('.stage1-album-card');

    expect(card.dataset['albumId']).toBe('album-a');
    expect(card.getAttribute('data-selected')).toBe('true');
    expect(card.getAttribute('data-glow')).toBe('true');
  });
});
