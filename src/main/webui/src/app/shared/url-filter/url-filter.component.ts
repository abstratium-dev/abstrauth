import { Component, Input, Output, EventEmitter, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

/**
 * Reusable URL-based filter component with XSS protection.
 * 
 * This component provides:
 * - Text input for filtering
 * - URL query parameter synchronization
 * - Inline clear button
 * - XSS protection by validating URL parameters
 * 
 * Usage:
 * <url-filter 
 *   [placeholder]="'Filter by...'"
 *   [itemCount]="filteredItems.length"
 *   [totalCount]="items.length"
 *   [itemLabel]="'items'"
 *   (filterChange)="onFilterChange($event)">
 * </url-filter>
 */
@Component({
  selector: 'url-filter',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './url-filter.component.html',
  styleUrl: './url-filter.component.scss'
})
export class UrlFilterComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  /** Placeholder text for the filter input */
  @Input() placeholder: string = 'Filter...';
  
  /** Number of items currently shown after filtering */
  @Input() itemCount: number = 0;
  
  /** Total number of items before filtering */
  @Input() totalCount: number = 0;
  
  /** Label for the items (e.g., 'accounts', 'clients') */
  @Input() itemLabel: string = 'items';

  /** Emits the current filter text whenever it changes */
  @Output() filterChange = new EventEmitter<string>();

  filterText: string = '';

  ngOnInit(): void {
    // Subscribe to URL query parameters with XSS protection
    this.route.queryParams.subscribe(params => {
      const filterParam = params['filter'];
      // The filter implementation protects against XSS (Cross-Site Scripting) attacks by:
      // - Validating that URL filter parameters are strings only
      // - Rejecting objects, arrays, or other complex types
      // - Using Angular's built-in query parameter sanitization
      // - Never executing or evaluating filter content as code
      if (filterParam && typeof filterParam === 'string') {
        this.filterText = filterParam;
      } else {
        this.filterText = '';
      }
      this.emitFilterChange();
    });
  }

  onFilterChange(): void {
    this.emitFilterChange();
    this.updateUrlParam();
  }

  clearFilter(): void {
    this.filterText = '';
    this.emitFilterChange();
    this.updateUrlParam();
  }

  private emitFilterChange(): void {
    this.filterChange.emit(this.filterText);
  }

  private updateUrlParam(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { filter: this.filterText || null },
      queryParamsHandling: 'merge'
    });
  }
}
