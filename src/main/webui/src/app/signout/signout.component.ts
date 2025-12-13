import { Component, inject, OnInit } from '@angular/core';
import { AuthService } from '../auth.service';

@Component({
  selector: 'signout',
  imports: [],
  templateUrl: './signout.component.html',
  styleUrl: './signout.component.scss'
})
export class SignoutComponent implements OnInit {
  private authService = inject(AuthService);

  ngOnInit(): void {
    this.authService.signout();
  }
}
