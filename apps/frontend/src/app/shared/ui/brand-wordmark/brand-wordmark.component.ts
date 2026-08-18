import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'rr-brand-wordmark',
  templateUrl: './brand-wordmark.component.html',
  styleUrl: './brand-wordmark.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandWordmarkComponent {
  @Input() admin = false;
}
