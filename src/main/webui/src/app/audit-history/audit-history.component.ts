import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { AuditEntry } from '../model.service';
import { Controller } from '../controller';

@Component({
  selector: 'app-audit-history',
  imports: [CommonModule],
  templateUrl: './audit-history.component.html',
  styleUrl: './audit-history.component.scss'
})
export class AuditHistoryComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private controller = inject(Controller);

  entityType = '';
  primaryKey = '';
  entries: AuditEntry[] = [];
  relatedEntries: AuditEntry[] = [];
  relatedEntityType: string | null = null;
  relatedLoading = false;
  relatedError: string | null = null;
  loading = true;
  error: string | null = null;

  ngOnInit(): void {
    this.entityType = this.route.snapshot.paramMap.get('entityType') ?? '';
    this.primaryKey = this.route.snapshot.paramMap.get('primaryKey') ?? '';

    if (!this.entityType || !this.primaryKey) {
      this.error = 'Missing entity type or primary key.';
      this.loading = false;
      return;
    }

    this.loadHistory();
  }

  async loadHistory(): Promise<void> {
    this.loading = true;
    this.error = null;

    try {
      this.entries = await this.controller.getAuditHistory(this.entityType, this.primaryKey);
    } catch (err: any) {
      if (err.status === 403) {
        this.error = 'You do not have permission to view this audit history.';
      } else if (err.status === 400) {
        this.error = err.error?.error ?? 'Invalid request.';
      } else {
        this.error = 'Failed to load audit history. Please try again.';
      }
    } finally {
      this.loading = false;
    }

    if (this.entityType === 'account') {
      this.loadRelatedHistory('account_role');
    }
  }

  async loadRelatedHistory(relatedType: string): Promise<void> {
    this.relatedEntityType = relatedType;
    this.relatedLoading = true;
    this.relatedError = null;

    try {
      this.relatedEntries = await this.controller.getRelatedAuditHistory(
        relatedType, this.entityType, this.primaryKey
      );
    } catch (err: any) {
      if (err.status === 403) {
        this.relatedError = 'You do not have permission to view related audit history.';
      } else {
        this.relatedError = 'Failed to load related audit history.';
      }
    } finally {
      this.relatedLoading = false;
    }
  }

  goBack(): void {
    window.history.back();
  }

  getRevTypeName(revType: number): string {
    switch (revType) {
      case 0: return 'INSERT';
      case 1: return 'UPDATE';
      case 2: return 'DELETE';
      default: return 'UNKNOWN';
    }
  }

  getRevTypeBadgeClass(revType: number): string {
    switch (revType) {
      case 0: return 'badge-success';
      case 1: return 'badge-primary';
      case 2: return 'badge-danger';
      default: return '';
    }
  }

  getEntityColumns(entry: AuditEntry): string[] {
    const metaKeys = new Set(['rev', 'revType', 'revTimestamp', 'username', 'correlationId', 'changeNote']);
    return Object.keys(entry).filter(k => !metaKeys.has(k));
  }

  formatEntityType(type: string): string {
    return type.replace(/_/g, ' ');
  }

  formatTimestamp(ts: number): string {
    return new Date(ts).toLocaleString();
  }
}
