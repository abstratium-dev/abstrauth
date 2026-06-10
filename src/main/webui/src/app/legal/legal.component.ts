import { Component, inject, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomainService } from '../domain.service';
import { ModelService } from '../model.service';

@Component({
  selector: 'app-legal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './legal.component.html',
  styleUrl: './legal.component.scss'
})
export class LegalComponent {
  copyrightYears: string;
  isCorrectDomain: boolean;
  legalContent$: Signal<string | null>;

  constructor() {
    const year = new Date().getFullYear();
    this.copyrightYears = year > 2026 ? `2026 - ${year}` : String(year);
    this.isCorrectDomain = inject(DomainService).isAbstratiumDomain;
    this.legalContent$ = inject(ModelService).legalContent$;
  }
}
