import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'home',
  imports: [CommonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  emailMismatchWarning: string | null = null;

  ngOnInit(): void {
    // Check for email mismatch warning from invite flow
    const warning = sessionStorage.getItem('emailMismatchWarning');
    if (warning) {
      this.emailMismatchWarning = warning;
      sessionStorage.removeItem('emailMismatchWarning');
    }
  }
}
