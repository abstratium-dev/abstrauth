import { Component } from '@angular/core';

@Component({
  selector: 'home',
  imports: [],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {

  generateRandomString(length: number) {
    let text = '';
    const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    
    for (let i = 0; i < length; i++) {
      text += possible.charAt(Math.floor(Math.random() * possible.length));
    }
    
    return text;
  }

  async generateCodeChallenge(codeVerifier: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    const digest = await window.crypto.subtle.digest('SHA-256', data);
    
    // Convert to base64url format
    const base64 = btoa(String.fromCharCode(...new Uint8Array(digest)));
    return base64
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '');
  }

  async login() {
    const codeVerifier = this.generateRandomString(128);
    console.log(codeVerifier);
    const codeChallenge = await this.generateCodeChallenge(codeVerifier);
    const state = this.generateRandomString(32);
    
    // Store for later use
    sessionStorage.setItem('code_verifier', codeVerifier);
    sessionStorage.setItem('state', state);
    
    const protocolHostPort = window.location.protocol + '//' + window.location.host;

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: 'abstrauth_admin_app',
      redirect_uri: protocolHostPort + '/admin/callback',
      scope: 'openid profile email',
      state: state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256'
    });
    
    window.location.href = `/oauth2/authorize?${params}`;
  }  
}
