import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OrgSelectionComponent } from './org-selection.component';
import { AuthService } from '../auth.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { WINDOW } from '../window.token';

// Custom jasmine matchers for Jasmine 5.x
beforeAll(() => {
  // Add custom matchers if needed
});

describe('OrgSelectionComponent', () => {
  let component: OrgSelectionComponent;
  let fixture: ComponentFixture<OrgSelectionComponent>;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let mockWindow: { location: { href: string } };

  const mockRequestId = 'test-request-123';

  const mockOrganisations = [
    { id: 'org-1', name: 'Test Organisation 1' },
    { id: 'org-2', name: 'Test Organisation 2' }
  ];

  let formSubmitSpy: jasmine.Spy;

  beforeEach(async () => {
    formSubmitSpy = spyOn(HTMLFormElement.prototype, 'submit').and.callFake(() => {});
    mockWindow = { location: { href: '' } };
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'getLastOrgId', 'setLastOrgId', 'clearLastOrgId', 'getOrgId'
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl']);

    await TestBed.configureTestingModule({
      imports: [
        OrgSelectionComponent,
        CommonModule,
        ReactiveFormsModule
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        FormBuilder,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => key === 'requestId' ? mockRequestId : null
              }
            }
          }
        },
        {
          provide: WINDOW,
          useValue: mockWindow
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrgSelectionComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    // Clear localStorage and sessionStorage
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();

    // Handle the HTTP requests triggered by ngOnInit
    const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
    orgReq.flush(mockOrganisations);

    expect(component).toBeTruthy();
  });

  describe('Initial Load', () => {
    it('should load organisations on init', () => {
      fixture.detectChanges();

      // Expect GET request for organisations
      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      expect(orgReq.request.method).toBe('GET');
      orgReq.flush(mockOrganisations);

      expect(component.organisations).toEqual(mockOrganisations);
      expect(component.isLoading).toBeFalse();
    });

    it('should show error when no organisations available', () => {
      fixture.detectChanges();

      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      orgReq.flush([]);

      expect(component.organisations.length).toBe(0);
      expect(component.errorMessage).toContain('not a member of any organisation');
      expect(component.isLoading).toBeFalse();
    });

    it('should auto-select single organisation', () => {
      fixture.detectChanges();

      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      orgReq.flush([mockOrganisations[0]]);

      expect(component.selectedOrgId).toBe('org-1');
      expect(component.orgSelectionForm.get('orgId')?.value).toBe('org-1');
    });
  });

  describe('LastOrgId Pre-selection', () => {
    it('should pre-select lastOrgId from localStorage if valid', () => {
      authServiceSpy.getLastOrgId.and.returnValue('org-2');

      fixture.detectChanges();

      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      orgReq.flush(mockOrganisations);

      expect(component.selectedOrgId).toBe('org-2');
      expect(component.orgSelectionForm.get('orgId')?.value).toBe('org-2');
    });

    it('should not pre-select if lastOrgId is not in available orgs', () => {
      authServiceSpy.getLastOrgId.and.returnValue('invalid-org-id');

      fixture.detectChanges();

      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      orgReq.flush(mockOrganisations);

      // Should not pre-select invalid org
      expect(component.selectedOrgId).toBe('');
    });
  });

  describe('Org Selection Submission', () => {
    beforeEach(() => {
      fixture.detectChanges();

      // Load organisations
      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      orgReq.flush(mockOrganisations);

      // Select an org
      component.selectedOrgId = 'org-1';
      component.orgSelectionForm.patchValue({ orgId: 'org-1' });
    });

    it('should submit org selection successfully', () => {
      component.selectOrg();

      const postReq = httpMock.expectOne('/api/org-selection');
      expect(postReq.request.method).toBe('POST');

      // Check form data - account_id is now extracted from OIDC token by backend
      const formData = postReq.request.body as string;
      expect(formData).toContain('request_id=test-request-123');
      expect(formData).toContain('org_id=org-1');
      expect(formData).not.toContain('account_id');

      postReq.flush({ consentRequired: true });

      expect(authServiceSpy.setLastOrgId).toHaveBeenCalledWith('org-1');
    });

    it('should submit consent form when selection succeeds', () => {
      component.selectOrg();

      const postReq = httpMock.expectOne('/api/org-selection');
      postReq.flush({ consentRequired: true });

      // Verify lastOrgId was stored and consent form was submitted
      expect(authServiceSpy.setLastOrgId).toHaveBeenCalledWith('org-1');
      expect(formSubmitSpy).toHaveBeenCalled();
    });

    it('should submit consent form regardless of consentRequired flag', () => {
      component.selectOrg();

      const postReq = httpMock.expectOne('/api/org-selection');
      postReq.flush({ consentRequired: false });

      expect(authServiceSpy.setLastOrgId).toHaveBeenCalledWith('org-1');
      expect(formSubmitSpy).toHaveBeenCalled();
    });

    it('should show error when submission fails', () => {
      component.selectOrg();

      const postReq = httpMock.expectOne('/api/org-selection');
      postReq.flush({ error: 'Not a member of selected org' }, { status: 403, statusText: 'Forbidden' });

      expect(component.errorMessage).toBeTruthy();
      expect(component.isSubmitting).toBeFalse();
    });

    it('should not submit without selected org', () => {
      component.selectedOrgId = '';
      component.orgSelectionForm.patchValue({ orgId: '' });

      component.selectOrg();

      httpMock.expectNone('/api/org-selection');
      expect(component.errorMessage).toContain('Please select');
    });
  });

  describe('Form Validation', () => {
    it('should mark form invalid without selection', () => {
      fixture.detectChanges();

      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      orgReq.flush(mockOrganisations);

      component.orgSelectionForm.patchValue({ orgId: '' });
      expect(component.orgSelectionForm.invalid).toBeTrue();
    });

    it('should mark form valid with selection', () => {
      fixture.detectChanges();

      const orgReq = httpMock.expectOne(`/api/org-selection/${mockRequestId}`);
      orgReq.flush(mockOrganisations);

      component.orgSelectionForm.patchValue({ orgId: 'org-1' });
      expect(component.orgSelectionForm.valid).toBeTrue();
    });
  });
});
