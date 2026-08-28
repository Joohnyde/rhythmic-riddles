import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmDialogComponent } from './confirm-dialog.component';

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let showModal: HTMLDialogElement['showModal'] | undefined;
  let close: HTMLDialogElement['close'] | undefined;

  beforeEach(async () => {
    showModal = HTMLDialogElement.prototype.showModal;
    close = HTMLDialogElement.prototype.close;
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
      this.querySelector<HTMLElement>('[autofocus]')?.focus();
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    });

    await TestBed.configureTestingModule({ imports: [ConfirmDialogComponent] }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('title', 'Choose album?');
    fixture.componentRef.setInput('message', 'Confirm this Stage 1 pick.');
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    if (showModal) HTMLDialogElement.prototype.showModal = showModal;
    else delete (HTMLDialogElement.prototype as Partial<HTMLDialogElement>).showModal;
    if (close) HTMLDialogElement.prototype.close = close;
    else delete (HTMLDialogElement.prototype as Partial<HTMLDialogElement>).close;
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('opens with accessible title/description relationships and native initial focus on Cancel', () => {
    fixture.componentInstance.open();
    fixture.detectChanges();

    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    const titleId = dialog.getAttribute('aria-labelledby');
    const descriptionId = dialog.getAttribute('aria-describedby');
    const cancelButton = dialog.querySelector<HTMLButtonElement>('button[autofocus]');

    expect(dialog.open).toBe(true);
    expect(titleId).toBeTruthy();
    expect(descriptionId).toBeTruthy();
    expect(fixture.nativeElement.querySelector(`#${titleId}`)?.textContent).toContain(
      'Choose album?',
    );
    expect(fixture.nativeElement.querySelector(`#${descriptionId}`)?.textContent).toContain(
      'Confirm this Stage 1 pick.',
    );
    expect(cancelButton).toBeTruthy();
    expect(document.activeElement).toBe(cancelButton);
  });

  it('emits confirm once, closes, and suppresses a duplicate confirm from the same open cycle', () => {
    const confirmed = vi.fn();
    fixture.componentInstance.confirmed.subscribe(confirmed);
    fixture.componentInstance.open();

    fixture.componentInstance.confirm();
    fixture.componentInstance.confirm();

    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    expect(confirmed).toHaveBeenCalledOnce();
    expect(dialog.open).toBe(false);
  });

  it('resets duplicate-confirm protection across reopen and supports the native button activation contract', () => {
    const confirmed = vi.fn();
    fixture.componentInstance.confirmed.subscribe(confirmed);
    const buttons = () =>
      Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'),
      );

    fixture.componentInstance.open();
    buttons()
      .find((button) => button.textContent?.includes('YES'))
      ?.click();
    fixture.componentInstance.open();
    buttons()
      .find((button) => button.textContent?.includes('YES'))
      ?.click();

    expect(confirmed).toHaveBeenCalledTimes(2);
  });

  it('keeps disabled confirm inert while Cancel remains available', () => {
    const confirmed = vi.fn();
    fixture.componentInstance.confirmed.subscribe(confirmed);
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    fixture.componentInstance.open();

    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    const confirmButton = Array.from(dialog.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.includes('YES'),
    );
    const cancelButton = Array.from(dialog.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.includes('CANCEL'),
    );
    confirmButton?.click();
    expect(dialog.open).toBe(true);
    expect(confirmed).not.toHaveBeenCalled();

    cancelButton?.click();
    expect(dialog.open).toBe(false);
  });

  it('handles the native Escape cancel event exactly once without allowing default double-close behavior', () => {
    fixture.componentInstance.open();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    const closeSpy = vi.mocked(HTMLDialogElement.prototype.close);
    const cancelEvent = new Event('cancel', { cancelable: true });

    dialog.dispatchEvent(cancelEvent);

    expect(cancelEvent.defaultPrevented).toBe(true);
    expect(closeSpy).toHaveBeenCalledOnce();
    expect(dialog.open).toBe(false);
  });
});
