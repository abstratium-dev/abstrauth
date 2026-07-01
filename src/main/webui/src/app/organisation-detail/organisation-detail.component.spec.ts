import type { MockedObject } from "vitest";
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { Controller } from '../controller';
import { ModelService, Organisation } from '../model.service';
import { ToastService } from '../shared/toast/toast.service';
import { OrganisationDetailComponent } from './organisation-detail.component';

describe('OrganisationDetailComponent', () => {
    let component: OrganisationDetailComponent;
    let fixture: ComponentFixture<OrganisationDetailComponent>;
    let httpMock: HttpTestingController;
    let toastService: MockedObject<ToastService>;

    const mockOrg: Organisation = {
        id: 'org-1',
        name: 'Acme Corp',
        createdAt: '2024-01-01T00:00:00',
        roles: ['owner', 'member']
    };

    const mockMemberOrg: Organisation = {
        id: 'org-2',
        name: 'Globex Inc',
        createdAt: '2024-02-01T00:00:00',
        roles: ['member']
    };

    beforeEach(async () => {
        const toastSpy = {
            success: vi.fn().mockName("ToastService.success"),
            error: vi.fn().mockName("ToastService.error")
        };

        await TestBed.configureTestingModule({
            imports: [OrganisationDetailComponent, RouterTestingModule],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(withXhr()),
                provideHttpClientTesting(),
                { provide: ToastService, useValue: toastSpy },
                {
                    provide: ActivatedRoute,
                    useValue: { snapshot: { paramMap: { get: (_: string) => 'org-1' } } }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(OrganisationDetailComponent);
        component = fixture.componentInstance;
        TestBed.inject(ModelService).reset();
        httpMock = TestBed.inject(HttpTestingController);
        toastService = TestBed.inject(ToastService) as MockedObject<ToastService>;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create', () => {
        fixture.detectChanges();
        httpMock.expectOne('/api/organisations/org-1').flush(mockOrg);
        expect(component).toBeTruthy();
    });

    describe('Initialisation', () => {
        it('should read orgId from route params', () => {
            fixture.detectChanges();
            httpMock.expectOne('/api/organisations/org-1').flush(mockOrg);
            expect(component.orgId).toBe('org-1');
        });

        it('should call GET /api/organisations/:orgId on init', () => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/organisations/org-1');
            expect(req.request.method).toBe('GET');
            req.flush(mockOrg);
        });

        it('should show loading state before data arrives', () => {
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('.loading')).toBeTruthy();
            httpMock.expectOne('/api/organisations/org-1').flush(mockOrg);
        });
    });

    describe('Displaying organisation', () => {
        beforeEach(() => {
            fixture.detectChanges();
            httpMock.expectOne('/api/organisations/org-1').flush(mockOrg);
            fixture.detectChanges();
        });

        it('should display org name', () => {
            expect(fixture.nativeElement.querySelector('#org-name').textContent.trim()).toBe('Acme Corp');
        });

        it('should display org id', () => {
            expect(fixture.nativeElement.querySelector('#org-id').textContent.trim()).toBe('org-1');
        });

        it('should show Edit Name button for owners', () => {
            expect(fixture.nativeElement.querySelector('#edit-button')).toBeTruthy();
        });

        it('should not show Edit Name button for members-only', () => {
            const controller = TestBed.inject(Controller);
            vi.spyOn(controller, 'loadOrganisation').mockImplementation(() => {
                const modelService = (controller as any).modelService;
                modelService.setCurrentOrganisation(mockMemberOrg);
            });
            component.ngOnInit();
            fixture.detectChanges();

            expect(component.isOwner()).toBe(false);
            expect(fixture.nativeElement.querySelector('#edit-button')).toBeFalsy();
        });
    });

    describe('Edit mode', () => {
        beforeEach(() => {
            fixture.detectChanges();
            httpMock.expectOne('/api/organisations/org-1').flush(mockOrg);
            fixture.detectChanges();
        });

        it('should enter edit mode when Edit Name clicked', () => {
            fixture.nativeElement.querySelector('#edit-button').click();
            fixture.detectChanges();

            expect(component.editMode()).toBe(true);
            expect(fixture.nativeElement.querySelector('#edit-org-name')).toBeTruthy();
        });

        it('should pre-fill edit input with current name', async () => {
            await Promise.resolve(); TestBed.flushEffects();
            fixture.nativeElement.querySelector('#edit-button').click();
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const input: HTMLInputElement = fixture.nativeElement.querySelector('#edit-org-name');
            expect(input.value).toBe('Acme Corp');
        });

        it('should exit edit mode on cancel', () => {
            component.startEdit();
            fixture.detectChanges();

            fixture.nativeElement.querySelector('#cancel-edit-button').click();
            fixture.detectChanges();

            expect(component.editMode()).toBe(false);
            expect(fixture.nativeElement.querySelector('#edit-org-name')).toBeFalsy();
        });

        it('should set formError when name is blank on submit', async () => {
            component.startEdit();
            component.editName = '   ';
            await component.onSubmitEdit();
            expect(component.formError()).toBeTruthy();
        });

        it('should PUT to /api/organisations/:orgId on submit', async () => {
            component.startEdit();
            component.editName = 'New Name';
            fixture.detectChanges();

            component.onSubmitEdit();
            await Promise.resolve(); TestBed.flushEffects();

            const req = httpMock.expectOne('/api/organisations/org-1');
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({ name: 'New Name' });
            req.flush({ ...mockOrg, name: 'New Name' });

            await Promise.resolve(); TestBed.flushEffects();
            httpMock.expectOne('/api/organisations').flush([]);
            httpMock.expectOne('/api/organisations/current').flush({ ...mockOrg, name: 'New Name' });
        });

        it('should show success toast and close edit mode after successful rename', async () => {
            component.startEdit();
            component.editName = 'Renamed Corp';

            component.onSubmitEdit();
            await Promise.resolve(); TestBed.flushEffects();

            httpMock.expectOne('/api/organisations/org-1').flush({ ...mockOrg, name: 'Renamed Corp' });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            httpMock.expectOne('/api/organisations').flush([]);
            httpMock.expectOne('/api/organisations/current').flush({ ...mockOrg, name: 'Renamed Corp' });

            expect(toastService.success).toHaveBeenCalledWith(expect.stringContaining('Renamed Corp'));
            expect(component.editMode()).toBe(false);
        });

        it('should show formError on 403 response', async () => {
            component.startEdit();
            component.editName = 'Forbidden';

            component.onSubmitEdit();
            await Promise.resolve(); TestBed.flushEffects();

            httpMock.expectOne('/api/organisations/org-1').flush(null, { status: 403, statusText: 'Forbidden' });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.formError()).toContain('permission');
            expect(component.formSubmitting()).toBe(false);
        });

        it('should show formError on 400 with violations', async () => {
            component.startEdit();
            component.editName = 'x';

            component.onSubmitEdit();
            await Promise.resolve(); TestBed.flushEffects();

            httpMock.expectOne('/api/organisations/org-1').flush({ violations: [{ message: 'Name too short' }] }, { status: 400, statusText: 'Bad Request' });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.formError()).toContain('Name too short');
        });

        it('should show generic error on unknown failure', async () => {
            component.startEdit();
            component.editName = 'Org';

            component.onSubmitEdit();
            await Promise.resolve(); TestBed.flushEffects();

            httpMock.expectOne('/api/organisations/org-1').flush(null, { status: 500, statusText: 'Server Error' });
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();

            expect(component.formError()).toContain('Failed to update');
        });
    });

    describe('isOwner computed signal', () => {
        it('should be true when roles include owner', () => {
            fixture.detectChanges();
            httpMock.expectOne('/api/organisations/org-1').flush(mockOrg);
            fixture.detectChanges();

            expect(component.isOwner()).toBe(true);
        });

        it('should be false when roles do not include owner', () => {
            fixture.detectChanges();
            httpMock.expectOne('/api/organisations/org-1').flush(mockMemberOrg);
            fixture.detectChanges();

            expect(component.isOwner()).toBe(false);
        });

        it('should be false when organisation is null', () => {
            fixture.detectChanges();
            httpMock.expectOne('/api/organisations/org-1').flush(null, { status: 404, statusText: 'Not Found' });
            fixture.detectChanges();

            expect(component.isOwner()).toBe(false);
        });
    });
});