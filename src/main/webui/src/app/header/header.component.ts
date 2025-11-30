import { Component, effect, inject } from '@angular/core';
import { AuthService } from '../auth.service';
import { Token } from '../auth.service';

@Component({
  selector: 'header',
  imports: [],
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

  signout() {
    this.authService.signout();
  }

}
