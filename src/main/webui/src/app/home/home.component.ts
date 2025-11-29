import { Component, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { HeaderComponent } from '../header/header.component';

@Component({
  selector: 'home',
  imports: [HeaderComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private router = inject(Router)
  private authService = inject(AuthService)

  constructor(
  ) {
    effect(() => {
      const token = this.authService.token$()
      if(!token.isAuthenticated) {
        console.info("not signed in, redirecting to sign in");
        this.router.navigate(['/authorize']);
      }
    })
  }
  
}
