import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DxButtonModule } from 'devextreme-angular';
import { ThemeService } from '../../services';

@Component({
  selector: 'theme-switcher',
  template: `
    <dx-button class="theme-button" stylingMode="text" [icon]="themeService.currentTheme === 'dark' ? 'sun' : 'moon'"
               (onClick)="onButtonClick()"></dx-button>`,
  styleUrls: [],
  standalone: true,
  imports: [CommonModule, DxButtonModule],
})
export class ThemeSwitcher {
  constructor(public themeService: ThemeService) {}

  onButtonClick () {
    this.themeService.switchTheme();
  }
}
