import { Component, inject, effect, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { AuthService, Token } from '../auth.service';

@Component({
  selector: 'user',
  imports: [CommonModule],
  templateUrl: './user.component.html',
  styleUrl: './user.component.scss',
})
export class UserComponent implements OnInit {
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  
  token!: Token;
  tokenClaims: { key: string; value: any }[] = [];
  errorMessage: string | null = null;
  routeUserId: string | null = null;

  constructor() {
    effect(() => {
      this.token = this.authService.token$();
      this.tokenClaims = this.extractClaims(this.token);
      this.validateUserId();
    });
  }

  ngOnInit(): void {
    this.routeUserId = this.route.snapshot.paramMap.get('id');
    this.validateUserId();
  }

  private validateUserId(): void {
    if (!this.routeUserId || !this.token) {
      return;
    }

    if (this.routeUserId !== this.token.sub) {
      this.errorMessage = `Access denied: The user ID in the URL (${this.routeUserId}) does not match your authenticated user ID (${this.token.sub}).`;
    } else {
      this.errorMessage = null;
    }
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
