import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Controller } from '../controller';
import { ModelService, OAuthClient } from '../model.service';
import { UrlFilterComponent } from '../shared/url-filter/url-filter.component';

@Component({
  selector: 'clients',
  imports: [CommonModule, RouterLink, UrlFilterComponent],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss',
})
export class ClientsComponent implements OnInit {
  private controller = inject(Controller);
  private modelService = inject(ModelService);
  
  clients: OAuthClient[] = [];
  filteredClients: OAuthClient[] = [];
  loading = true;
  error: string | null = null;

  constructor() {
    effect(() => {
      this.clients = this.modelService.clients$();
      this.loading = this.modelService.clientsLoading$();
      this.error = this.modelService.clientsError$();
      this.applyFilter();
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

  onFilterChange(filterText: string): void {
    const searchTerm = filterText.toLowerCase().trim();
    
    if (!searchTerm) {
      this.filteredClients = this.clients;
      return;
    }

    this.filteredClients = this.clients.filter(client => {
      // Search in client name
      if (client.clientName.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in client ID
      if (client.clientId.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in client type
      if (client.clientType.toLowerCase().includes(searchTerm)) {
        return true;
      }
      // Search in redirect URIs
      const redirectUris = this.parseJsonArray(client.redirectUris);
      if (redirectUris.some(uri => uri.toLowerCase().includes(searchTerm))) {
        return true;
      }
      // Search in allowed scopes
      const scopes = this.parseJsonArray(client.allowedScopes);
      if (scopes.some(scope => scope.toLowerCase().includes(searchTerm))) {
        return true;
      }
      return false;
    });
  }

  private applyFilter(): void {
    // Called from effect when clients change
    this.filteredClients = this.clients;
  }
}
