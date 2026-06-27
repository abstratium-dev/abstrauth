import { Component, inject, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DomainService } from '../domain.service';
import { ModelService } from '../model.service';

@Component({
  selector: 'app-legal',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './legal.component.html',
  styleUrl: './legal.component.scss'
})
export class LegalComponent {
  copyrightYears: string;
  isCorrectDomain: boolean;
  legalContent$: Signal<string | null>;
  auditRetentionDays$: Signal<number>;

  constructor() {
    const year = new Date().getFullYear();
    this.copyrightYears = year > 2026 ? `2026 - ${year}` : String(year);
    this.isCorrectDomain = inject(DomainService).isAbstratiumDomain;
    const modelService = inject(ModelService);
    this.legalContent$ = modelService.legalContent$;
    this.auditRetentionDays$ = modelService.auditRetentionDays$;
  }
}
