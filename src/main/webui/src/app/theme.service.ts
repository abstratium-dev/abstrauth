import { Injectable, signal, effect } from '@angular/core';

export type Theme = 'light' | 'dark';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'abstrauth-theme';
  
  // Signal to hold the current theme
  theme$ = signal<Theme>(this.getInitialTheme());

  constructor() {
    // Apply theme whenever it changes
    effect(() => {
      const theme = this.theme$();
      this.applyTheme(theme);
      this.saveTheme(theme);
    });
  }

  /**
   * Toggle between light and dark themes
   */
  toggleTheme(): void {
    const currentTheme = this.theme$();
    const newTheme: Theme = currentTheme === 'light' ? 'dark' : 'light';
    this.theme$.set(newTheme);
  }

  /**
   * Set a specific theme
   */
  setTheme(theme: Theme): void {
    this.theme$.set(theme);
  }

  /**
   * Get the initial theme based on saved preference or system preference
   */
  private getInitialTheme(): Theme {
    // Check if there's a saved theme preference
    const savedTheme = localStorage.getItem(this.THEME_KEY) as Theme | null;
    if (savedTheme === 'light' || savedTheme === 'dark') {
      return savedTheme;
    }

    // Fall back to system preference
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }

    return 'light';
  }

  /**
   * Apply the theme to the document
   */
  private applyTheme(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }

  /**
   * Save the theme preference to localStorage
   */
  private saveTheme(theme: Theme): void {
    localStorage.setItem(this.THEME_KEY, theme);
  }
}
