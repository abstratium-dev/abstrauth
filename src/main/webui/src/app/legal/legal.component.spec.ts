import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { signal } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { LegalComponent } from './legal.component';
import { DomainService } from '../domain.service';
import { ModelService } from '../model.service';

function makeMocks(isAbstratiumDomain: boolean, legalContent: string | null, auditRetentionDays: number = 90) {
    const mockDomainService = { isAbstratiumDomain };
    const mockModelService = {
        legalContent$: signal(legalContent),
        auditRetentionDays$: signal(auditRetentionDays)
    };
    return { mockDomainService, mockModelService };
}

async function buildFixture(isAbstratiumDomain: boolean, legalContent: string | null): Promise<ComponentFixture<LegalComponent>> {
    TestBed.resetTestingModule();
    const { mockDomainService, mockModelService } = makeMocks(isAbstratiumDomain, legalContent);
    const routerEventsSubject = new Subject();
    const mockRouter = {
        navigate: vi.fn().mockName('navigate'),
        url: '/',
        events: routerEventsSubject.asObservable(),
        createUrlTree: () => ({ root: { segments: [], children: {}, hasChildren: false, numberOfChildren: 0 }, queryParams: {}, fragment: null, queryParamMap: { get: () => null, getAll: () => [], has: () => false } }),
        serializeUrl: () => '/'
    };
    const mockActivatedRoute = { snapshot: {} };
    await TestBed.configureTestingModule({
        imports: [LegalComponent],
        providers: [
            provideZonelessChangeDetection(),
            { provide: DomainService, useValue: mockDomainService },
            { provide: ModelService, useValue: mockModelService },
            { provide: Router, useValue: mockRouter },
            { provide: ActivatedRoute, useValue: mockActivatedRoute },
        ]
    }).compileComponents();
    const f = TestBed.createComponent(LegalComponent);
    f.detectChanges();
    return f;
}

describe('LegalComponent', () => {
    let component: LegalComponent;
    let fixture: ComponentFixture<LegalComponent>;

    beforeEach(async () => {
        fixture = await buildFixture(true, null);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute copyrightYears', () => {
        expect(component.copyrightYears).toBeTruthy();
        const currentYear = new Date().getFullYear();
        expect(component.copyrightYears).toContain(String(currentYear));
    });

    describe('Domain check via DomainService', () => {
        it('should reflect isCorrectDomain true when DomainService reports abstratium domain', async () => {
            const f = await buildFixture(true, null);
            expect(f.componentInstance.isCorrectDomain).toBe(true);
        });

        it('should reflect isCorrectDomain false when DomainService reports foreign domain', async () => {
            const f = await buildFixture(false, null);
            expect(f.componentInstance.isCorrectDomain).toBe(false);
        });
    });

    describe('Custom legal content (ABSTRA_LEGAL_CONTENT_FILE)', () => {
        it('should render custom content and hide abstratium sections when legalContent is set', async () => {
            const f = await buildFixture(false, '<p class="custom-legal">My Legal Text</p>');
            const el = f.nativeElement as HTMLElement;
            expect(el.querySelector('.custom-legal')).toBeTruthy();
            expect(el.querySelector('.legal-title')).toBeNull();
            expect(el.querySelector('.misconfiguration-warning')).toBeNull();
        });

        it('should show abstratium content when legalContent is null', async () => {
            const f = await buildFixture(true, null);
            const el = f.nativeElement as HTMLElement;
            expect(el.querySelector('.legal-title')).toBeTruthy();
            expect(el.querySelector('#custom-legal')).toBeNull();
        });
    });

    describe('Misconfiguration warning', () => {
        it('should show warning when not on abstratium domain and no custom content', async () => {
            const f = await buildFixture(false, null);
            const el = f.nativeElement as HTMLElement;
            expect(el.querySelector('.misconfiguration-warning')).toBeTruthy();
        });

        it('should not show warning when on abstratium domain', async () => {
            const f = await buildFixture(true, null);
            const el = f.nativeElement as HTMLElement;
            expect(el.querySelector('.misconfiguration-warning')).toBeNull();
        });
    });

    describe('Template Rendering (abstratium domain)', () => {
        it('should display legal page heading', () => {
            const compiled = fixture.nativeElement as HTMLElement;
            const heading = compiled.querySelector('.legal-title');
            expect(heading).toBeTruthy();
            expect(heading?.textContent).toContain('Legal');
        });

        it('should render copyright notice section', () => {
            const titles = Array.from(fixture.nativeElement.querySelectorAll('.notice-card-title')).map((el: any) => el.textContent);
            expect(titles).toContain('Copyright Notice');
        });

        it('should render AI transparency section', () => {
            const titles = Array.from(fixture.nativeElement.querySelectorAll('.notice-card-title')).map((el: any) => el.textContent);
            expect(titles).toContain('AI Transparency & Authorship Notice');
        });

        it('should render terms of use section', () => {
            const titles = Array.from(fixture.nativeElement.querySelectorAll('.notice-card-title')).map((el: any) => el.textContent);
            expect(titles).toContain('Terms of Use & Disclaimer');
        });

        it('should render privacy section', () => {
            const titles = Array.from(fixture.nativeElement.querySelectorAll('.notice-card-title')).map((el: any) => el.textContent);
            expect(titles).toContain('Privacy & Data Protection');
        });

        it('should display the configured audit retention period', () => {
            const compiled = fixture.nativeElement as HTMLElement;
            expect(compiled.textContent).toContain('90 days');
            expect(compiled.textContent).toContain('Audit history');
        });

        it('should render contact section', () => {
            const titles = Array.from(fixture.nativeElement.querySelectorAll('.notice-card-title')).map((el: any) => el.textContent);
            expect(titles).toContain('Contact');
        });

        it('should render contact image', () => {
            const img = fixture.nativeElement.querySelector('.contact-card-img');
            expect(img).toBeTruthy();
            expect(img?.getAttribute('src')).toBe('https://abstratium.dev/contact.png');
        });
    });
});
