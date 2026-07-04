import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { UrlFilterComponent } from './url-filter.component';

describe('UrlFilterComponent', () => {
    let component: UrlFilterComponent;
    let fixture: ComponentFixture<UrlFilterComponent>;
    let queryParams: Subject<Record<string, unknown>>;
    let router: Router;
    let navigateSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        queryParams = new Subject<Record<string, unknown>>();

        await TestBed.configureTestingModule({
            imports: [UrlFilterComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { queryParams } },
                { provide: Router, useValue: { navigate: vi.fn() } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(UrlFilterComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        navigateSpy = vi.spyOn(router, 'navigate');
        fixture.detectChanges();
    });

    afterEach(() => {
        TestBed.resetTestingModule();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load filter text from URL query param', () => {
        vi.spyOn(component.filterChange, 'emit');
        queryParams.next({ filter: 'searchTerm' });
        fixture.detectChanges();

        expect(component.filterText).toBe('searchTerm');
        expect(component.filterChange.emit).toHaveBeenCalledWith('searchTerm');
    });

    it('should ignore non-string filter query param', () => {
        vi.spyOn(component.filterChange, 'emit');
        queryParams.next({ filter: { bad: 'object' } });
        fixture.detectChanges();

        expect(component.filterText).toBe('');
        expect(component.filterChange.emit).toHaveBeenCalledWith('');
    });

    it('should treat array filter query param as empty', () => {
        vi.spyOn(component.filterChange, 'emit');
        queryParams.next({ filter: ['a', 'b'] });
        fixture.detectChanges();

        expect(component.filterText).toBe('');
        expect(component.filterChange.emit).toHaveBeenCalledWith('');
    });

    it('should emit filterChange and update URL on input', () => {
        vi.spyOn(component.filterChange, 'emit');
        component.filterText = 'test';
        component.onFilterChange();
        fixture.detectChanges();

        expect(component.filterChange.emit).toHaveBeenCalledWith('test');
        expect(navigateSpy).toHaveBeenCalledWith([], {
            relativeTo: TestBed.inject(ActivatedRoute),
            queryParams: { filter: 'test' },
            queryParamsHandling: 'merge'
        });
    });

    it('should clear filter and update URL when clear button is clicked', () => {
        vi.spyOn(component.filterChange, 'emit');
        component.filterText = 'search';
        fixture.detectChanges();

        const clearButton = fixture.nativeElement.querySelector('.filter-clear-button') as HTMLButtonElement;
        expect(clearButton).toBeTruthy();
        clearButton.click();
        fixture.detectChanges();

        expect(component.filterText).toBe('');
        expect(component.filterChange.emit).toHaveBeenCalledWith('');
        expect(navigateSpy).toHaveBeenCalledWith([], {
            relativeTo: TestBed.inject(ActivatedRoute),
            queryParams: { filter: null },
            queryParamsHandling: 'merge'
        });
    });

    it('should not render clear button when filter is empty', () => {
        component.filterText = '';
        fixture.detectChanges();

        const clearButton = fixture.nativeElement.querySelector('.filter-clear-button');
        expect(clearButton).toBeFalsy();
    });

    it('should render custom placeholder', () => {
        component.placeholder = 'Find accounts...';
        fixture.detectChanges();

        const input = fixture.nativeElement.querySelector('#filter-input') as HTMLInputElement;
        expect(input.getAttribute('placeholder')).toBe('Find accounts...');
    });

    it('should render item count info', () => {
        component.itemCount = 2;
        component.totalCount = 5;
        component.itemLabel = 'accounts';
        fixture.detectChanges();

        const info = fixture.nativeElement.querySelector('.filter-info') as HTMLElement;
        expect(info.textContent).toContain('Showing 2 of 5 accounts');
    });

    it('should emit filterChange when URL param is empty', () => {
        vi.spyOn(component.filterChange, 'emit');
        queryParams.next({ filter: '' });
        fixture.detectChanges();

        expect(component.filterText).toBe('');
        expect(component.filterChange.emit).toHaveBeenCalledWith('');
    });
});
