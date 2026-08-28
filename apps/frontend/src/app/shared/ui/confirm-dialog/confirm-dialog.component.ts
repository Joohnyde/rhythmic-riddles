import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
  input,
  output,
} from '@angular/core';

let nextConfirmDialogId = 0;

@Component({
  selector: 'rr-confirm-dialog',
  standalone: true,
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  @ViewChild('dialog', { static: true }) private readonly dialog?: ElementRef<HTMLDialogElement>;

  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('YES');
  readonly cancelLabel = input('CANCEL');
  readonly disabled = input(false);
  readonly confirmed = output<void>();
  readonly titleId = `confirm-dialog-title-${++nextConfirmDialogId}`;
  readonly descriptionId = `confirm-dialog-description-${nextConfirmDialogId}`;

  private confirming = false;

  open(): void {
    this.confirming = false;
    this.dialog?.nativeElement.showModal();
  }

  cancel(): void {
    this.confirming = false;
    this.dialog?.nativeElement.close();
  }

  onNativeCancel(event: Event): void {
    // Own the native cancel lifecycle explicitly so Escape cannot trigger both our close path and
    // the dialog element's default close behavior.
    event.preventDefault();
    this.cancel();
  }

  confirm(): void {
    if (this.disabled() || this.confirming) {
      return;
    }

    this.confirming = true;
    this.dialog?.nativeElement.close();
    this.confirmed.emit();
  }
}
