import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
  input,
  output,
} from '@angular/core';

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

  open(): void {
    this.dialog?.nativeElement.showModal();
  }

  cancel(): void {
    this.dialog?.nativeElement.close();
  }

  confirm(): void {
    if (this.disabled()) {
      return;
    }

    this.dialog?.nativeElement.close();
    this.confirmed.emit();
  }
}
