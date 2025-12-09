import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit } from '@angular/core';
import { Controller } from '../controller';
import { ModelService, OAuthClient } from '../model.service';

@Component({
  selector: 'clients',
  imports: [CommonModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss',
})
export class ClientsComponent implements OnInit {
  private controller = inject(Controller);
  private modelService = inject(ModelService);
  
  clients: OAuthClient[] = [];
  loading = true;
  error: string | null = null;

  constructor() {
    effect(() => {
      this.clients = this.modelService.clients$();
      this.loading = this.modelService.clientsLoading$();
      this.error = this.modelService.clientsError$();
    });
  }

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.controller.loadClients();
  }

  parseJsonArray(jsonString: string): string[] {
    try {
      return JSON.parse(jsonString);
    } catch (e) {
      return [];
    }
  }
}
