import { Component, effect } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'home',
  imports: [],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {

  isSignedIn = false;

  constructor(
    private router: Router,
    private authService: AuthService
  ) {
    effect(() => {
      const token = this.authService.token$()
      this.isSignedIn = token.isAuthenticated
      console.info("isSignedIn", this.isSignedIn)
      console.info("email", token.email)
    })
  }

  signin() {
    this.router.navigate(['/authorize']);
  }

  accessToken() {
    return JSON.stringify(this.authService.getAccessToken(), null, 2)
  }
}
