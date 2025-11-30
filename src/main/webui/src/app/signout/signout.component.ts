import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'signout',
  imports: [],
  templateUrl: './signout.component.html',
  styleUrl: './signout.component.scss'
})
export class SignoutComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    this.authService.signout();
    this.router.navigate(['/']);
  }
}
