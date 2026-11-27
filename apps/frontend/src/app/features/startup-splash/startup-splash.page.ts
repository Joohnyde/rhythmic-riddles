import { ChangeDetectionStrategy, Component } from '@angular/core';

import { BrandLogoComponent } from '../../shared/ui/brand-logo/brand-logo.component';

@Component({
  selector: 'rr-startup-splash-page',
  imports: [BrandLogoComponent],
  templateUrl: './startup-splash.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StartupSplashPage {}
