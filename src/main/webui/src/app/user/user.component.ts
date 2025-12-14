import { CommonModule } from '@angular/common';
import { Component, effect, inject } from '@angular/core';
import { AuthService, Token } from '../auth.service';

@Component({
  selector: 'user',
  imports: [CommonModule],
  templateUrl: './user.component.html',
  styleUrl: './user.component.scss',
})
export class UserComponent {
  private authService = inject(AuthService);
  
  token!: Token;
  tokenClaims: { key: string; value: any }[] = [];

  constructor() {
    effect(() => {
      this.token = this.authService.token$();
      this.tokenClaims = this.extractClaims(this.token);
    });
  }

  private extractClaims(token: Token): { key: string; value: any }[] {
    return Object.entries(token).map(([key, value]) => ({
      key,
      value: this.formatValue(value)
    }));
  }

  private formatValue(value: any): any {
    if (Array.isArray(value)) {
      return value.length > 0 ? value : '[]';
    }
    if (typeof value === 'number') {
      // Check if it's a timestamp (iat, exp)
      if (value > 1000000000000) {
        return new Date(value).toISOString();
      }
      if (value > 1000000000) {
        return new Date(value * 1000).toISOString();
      }
    }
    if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    }
    return value;
  }

  isArray(value: any): boolean {
    return Array.isArray(value);
  }
}
