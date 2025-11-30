import { Component, effect, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../auth.service';

@Component({
  selector: 'header',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
})
export class HeaderComponent {
  private authService = inject(AuthService)

  token!: Token;
  isSignedIn = false;

  constructor(
  ) {
    effect(() => {
      this.token = this.authService.token$()
      this.isSignedIn = this.token.isAuthenticated
      console.info("header isSignedIn", this.isSignedIn)
      console.info("header roles: ", this.token.groups)
    })
  }
}
