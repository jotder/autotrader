import { Injectable } from '@angular/core';
import themes from 'devextreme/ui/themes';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  constructor() {
    try {
      themes.current('material.blue.dark.compact');
    } catch (e) {
      console.warn('DevExtreme theme init warning:', e);
    }
  }
}
