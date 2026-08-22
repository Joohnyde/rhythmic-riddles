import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'rr-brand-logo',
  templateUrl: './brand-logo.component.html',
  styleUrl: './brand-logo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandLogoComponent {
  @Input() admin = false;
}
