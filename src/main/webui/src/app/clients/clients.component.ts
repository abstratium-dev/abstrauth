import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';

interface OAuthClient {
  id: string;
  clientId: string;
  clientName: string;
  clientType: string;
  redirectUris: string;
  allowedScopes: string;
  requirePkce: boolean;
  createdAt: string;
}

@Component({
  selector: 'clients',
  imports: [CommonModule],
  templateUrl: './clients.component.html',
  styleUrl: './clients.component.scss',
})
export class ClientsComponent implements OnInit {
  private http = inject(HttpClient);
  
  clients: OAuthClient[] = [];
  loading = true;
  error: string | null = null;

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.loading = true;
    this.error = null;
    
    this.http.get<OAuthClient[]>('/api/clients')
      .subscribe({
        next: (data) => {
          this.clients = data;
          this.loading = false;
        },
        error: (err) => {
          this.error = 'Failed to load clients';
          this.loading = false;
          console.error('Error loading clients:', err);
        }
      });
  }

  parseJsonArray(jsonString: string): string[] {
    try {
      return JSON.parse(jsonString);
    } catch (e) {
      return [];
    }
  }
}
