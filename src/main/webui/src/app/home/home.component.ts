import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DomainService } from '../domain.service';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'home',
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
    private router = inject(Router);
    isAbstratiumDomain = inject(DomainService).isAbstratiumDomain;

    signin() {
        this.router.navigate(['/authorize']);
    }
}
