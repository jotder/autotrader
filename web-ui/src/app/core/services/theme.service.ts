import { Injectable, signal } from '@angular/core';
import themes from 'devextreme/ui/themes';

export type Theme = 'dark' | 'light';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private currentTheme = signal<Theme>('dark');
  theme = this.currentTheme.asReadonly();

  constructor() {
    const saved = localStorage.getItem('at-theme') as Theme;
    // Don't re-apply if it matches the index.html default (dark)
    if (saved && saved !== 'dark') {
      this.setTheme(saved);
    } else {
      // Just ensure signal is correct
      this.currentTheme.set('dark');
      // Body class for the initial load
      this.updateBodyClass('material.blue.dark.compact');
    }
  }

  toggleTheme() {
    this.setTheme(this.currentTheme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: Theme) {
    this.currentTheme.set(theme);
    localStorage.setItem('at-theme', theme);
    this.applyTheme(theme);
  }

  private applyTheme(theme: Theme) {
    const themeBundle = theme === 'dark' ? 'theme-dark' : 'theme-light';
    const dxThemeName = theme === 'dark' ? 'material.blue.dark.compact' : 'material.blue.light.compact';

    let link = document.getElementById('at-theme-link') as HTMLLinkElement;
    if (!link) {
      link = document.createElement('link');
      link.id = 'at-theme-link';
      link.rel = 'stylesheet';
      document.head.appendChild(link);
    }

    const href = `${themeBundle}.css`;
    
    if (link.getAttribute('href') === href) {
      this.safeCurrent(dxThemeName);
      return;
    }

    link.onload = () => {
      // Small delay to ensure browser parsed the CSS markers
      setTimeout(() => {
        this.safeCurrent(dxThemeName);
        this.updateBodyClass(dxThemeName);
      }, 50);
    };

    link.href = href;
  }

  private safeCurrent(name: string) {
    try {
      themes.current(name);
    } catch (e) {
      console.warn('DevExtreme theme switch warning:', e);
    }
  }

  private updateBodyClass(dxThemeName: string) {
    const body = document.body;
    const classes = Array.from(body.classList).filter(c => c.startsWith('dx-theme-'));
    body.classList.remove(...classes);
    body.classList.add(`dx-theme-${dxThemeName.replace(/\./g, '-')}`);
  }
}
