import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Controller } from '../controller';
import { AuditEntry } from '../model.service';

@Component({
  selector: 'app-audit-history',
  imports: [CommonModule],
  templateUrl: './audit-history.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './audit-history.component.scss'
})
export class AuditHistoryComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private controller = inject(Controller);

  entityType = '';
  primaryKey = '';
  entries = signal<AuditEntry[]>([]);
  relatedEntries = signal<AuditEntry[]>([]);
  relatedEntityType = signal<string | null>(null);
  relatedLoading = signal(false);
  relatedError = signal<string | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.entityType = this.route.snapshot.paramMap.get('entityType') ?? '';
    this.primaryKey = this.route.snapshot.paramMap.get('primaryKey') ?? '';

    if (!this.entityType || !this.primaryKey) {
      this.error.set('Missing entity type or primary key.');
      this.loading.set(false);
      return;
    }

    this.loadHistory();
  }

  async loadHistory(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);

    try {
      this.entries.set(await this.controller.getAuditHistory(this.entityType, this.primaryKey));
    } catch (err: any) {
      if (err.status === 403) {
        this.error.set('You do not have permission to view this audit history.');
      } else if (err.status === 400) {
        this.error.set(err.error?.error ?? 'Invalid request.');
      } else {
        this.error.set('Failed to load audit history. Please try again.');
      }
    } finally {
      this.loading.set(false);
    }

    if (this.entityType === 'account') {
      this.loadRelatedHistory('account_role');
    }
  }

  async loadRelatedHistory(relatedType: string): Promise<void> {
    this.relatedEntityType.set(relatedType);
    this.relatedLoading.set(true);
    this.relatedError.set(null);

    try {
      this.relatedEntries.set(await this.controller.getRelatedAuditHistory(
        relatedType, this.entityType, this.primaryKey
      ));
    } catch (err: any) {
      if (err.status === 403) {
        this.relatedError.set('You do not have permission to view related audit history.');
      } else {
        this.relatedError.set('Failed to load related audit history.');
      }
    } finally {
      this.relatedLoading.set(false);
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
