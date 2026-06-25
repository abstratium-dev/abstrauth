import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AuthService } from '../auth.service';
import { Controller } from '../controller';
import { Organisation } from '../model.service';
import { ToastService } from '../shared/toast/toast.service';
import { OrganisationsComponent } from './organisations.component';

describe('OrganisationsComponent', () => {
  let component: OrganisationsComponent;
  let fixture: ComponentFixture<OrganisationsComponent>;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;
  let toastService: jasmine.SpyObj<ToastService>;

  const mockOrgs: Organisation[] = [
    {
      id: 'org-1',
      name: 'Acme Corp',
      createdAt: '2024-01-01T00:00:00',
      roles: ['owner', 'member']
    },
    {
      id: 'org-2',
      name: 'Globex Inc',
      createdAt: '2024-02-01T00:00:00',
      roles: ['member']
    }
  ];

  beforeEach(async () => {
    const authSpy = jasmine.createSpyObj('AuthService', ['getOrgId', 'isAdmin']);
    authSpy.getOrgId.and.returnValue('org-1');
    authSpy.isAdmin.and.returnValue(false);

    const toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [OrganisationsComponent, RouterTestingModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authSpy },
        { provide: ToastService, useValue: toastSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrganisationsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/organisations').flush([]);
    expect(component).toBeTruthy();
  });

  describe('Initialisation', () => {
    it('should start with loading state true', () => {
      expect(component.loading).toBe(true);
    });

    it('should start with empty organisations array', () => {
      expect(component.organisations).toEqual([]);
    });

    it('should start with no error', () => {
      expect(component.error).toBeNull();
    });

    it('should call loadOrganisations on init', () => {
      const controller = TestBed.inject(Controller);
      spyOn(controller, 'loadOrganisations');
      component.ngOnInit();
      expect(controller.loadOrganisations).toHaveBeenCalled();
    });

    it('should display loading message while fetching', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      expect(compiled.querySelector('.loading')).toBeTruthy();
      expect(compiled.querySelector('.loading').textContent).toContain('Loading organisations');
      httpMock.expectOne('/api/organisations').flush([]);
    });
  });

  describe('Loading Organisations - Success', () => {
    it('should load organisations successfully', () => {
      fixture.detectChanges();
      const req = httpMock.expectOne('/api/organisations');
      expect(req.request.method).toBe('GET');
      req.flush(mockOrgs);
      fixture.detectChanges();

      expect(component.organisations).toEqual(mockOrgs);
      expect(component.loading).toBe(false);
      expect(component.error).toBeNull();
    });

    it('should display organisation tiles after successful load', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const tiles = fixture.nativeElement.querySelectorAll('.tile');
      expect(tiles.length).toBe(2);
    });

    it('should display organisation name in tile', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Acme Corp');
      expect(compiled.textContent).toContain('Globex Inc');
    });

    it('should display organisation ID in tile subtitle', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush([mockOrgs[0]]);
      fixture.detectChanges();

      const subtitle = fixture.nativeElement.querySelector('.tile-subtitle');
      expect(subtitle.textContent).toContain('org-1');
    });

    it('should show "Current" badge for the active organisation', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const badges = fixture.nativeElement.querySelectorAll('.badge-verified');
      expect(badges.length).toBe(1);
      expect(badges[0].textContent).toContain('Current');
    });

    it('should highlight the current organisation tile', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const tiles = fixture.nativeElement.querySelectorAll('.tile');
      expect(tiles[0].classList).toContain('highlighted-tile');
      expect(tiles[1].classList).not.toContain('highlighted-tile');
    });

    it('should not show "Current" badge when no org matches', () => {
      authService.getOrgId.and.returnValue(undefined);
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const badges = fixture.nativeElement.querySelectorAll('.badge-verified');
      expect(badges.length).toBe(0);
    });

    it('should display role badges for each org', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const tiles = fixture.nativeElement.querySelectorAll('.tile');
      const firstTileBadges = tiles[0].querySelectorAll('.badge:not(.badge-verified)');
      expect(firstTileBadges.length).toBe(2);
      expect(firstTileBadges[0].textContent.trim()).toBe('owner');
      expect(firstTileBadges[1].textContent.trim()).toBe('member');
    });

    it('should apply badge-primary class to owner role', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const tiles = fixture.nativeElement.querySelectorAll('.tile');
      const ownerBadge = tiles[0].querySelector('.badge-primary');
      expect(ownerBadge).toBeTruthy();
      expect(ownerBadge.textContent.trim()).toBe('owner');
    });

    it('should apply badge-native class to member role', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const tiles = fixture.nativeElement.querySelectorAll('.tile');
      const memberBadge = tiles[1].querySelector('.badge-native');
      expect(memberBadge).toBeTruthy();
      expect(memberBadge.textContent.trim()).toBe('member');
    });

    it('should show only member badge for member-only org', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();

      const tiles = fixture.nativeElement.querySelectorAll('.tile');
      const secondTileBadges = tiles[1].querySelectorAll('.badge:not(.badge-verified)');
      expect(secondTileBadges.length).toBe(1);
      expect(secondTileBadges[0].textContent.trim()).toBe('member');
    });
  });

  describe('Loading Organisations - Empty & Error', () => {
    it('should display empty message when no organisations', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush([]);
      fixture.detectChanges();

      const infoMessage = fixture.nativeElement.querySelector('.info-message');
      expect(infoMessage).toBeTruthy();
      expect(infoMessage.textContent).toContain('not a member of any organisations');
    });

    it('should display error message on HTTP failure', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      const errorBox = fixture.nativeElement.querySelector('.error-box');
      expect(errorBox).toBeTruthy();
      expect(component.error).toBeTruthy();
    });

    it('should reload organisations on retry', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      component.retry();
      const req = httpMock.expectOne('/api/organisations');
      req.flush(mockOrgs);
      fixture.detectChanges();

      expect(component.organisations).toEqual(mockOrgs);
    });
  });

  describe('Create Organisation Form', () => {
    beforeEach(() => {
      fixture.detectChanges();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
      fixture.detectChanges();
    });

    it('should not show create form initially', () => {
      expect(component.showCreateForm).toBe(false);
      expect(fixture.nativeElement.querySelector('.form-container')).toBeFalsy();
    });

    it('should show create form when button clicked', () => {
      component.toggleCreateForm();
      fixture.detectChanges();

      expect(component.showCreateForm).toBe(true);
      expect(fixture.nativeElement.querySelector('.form-container')).toBeTruthy();
    });

    it('should show "Cancel" button text when form is open', () => {
      component.toggleCreateForm();
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector('#create-org-button');
      expect(btn.textContent.trim()).toBe('Cancel');
    });

    it('should hide form and reset state on cancel', () => {
      component.toggleCreateForm();
      component.newOrgName = 'Test';
      component.toggleCreateForm();

      expect(component.showCreateForm).toBe(false);
      expect(component.newOrgName).toBe('');
      expect(component.formError).toBeNull();
    });

    it('should set formError when name is blank on submit', async () => {
      component.newOrgName = '   ';
      await component.onSubmitCreate();
      expect(component.formError).toBeTruthy();
    });

    it('should POST to /api/organisations with correct body', fakeAsync(() => {
      component.toggleCreateForm();
      fixture.detectChanges();

      component.newOrgName = 'New Org';
      fixture.detectChanges();

      component.onSubmitCreate();
      tick();

      const req = httpMock.expectOne('/api/organisations');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ name: 'New Org' });
      req.flush(mockOrgs[0]);

      tick();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);
    }));

    it('should show success toast and close form on successful create', fakeAsync(() => {
      component.toggleCreateForm();
      component.newOrgName = 'New Org';

      component.onSubmitCreate();
      tick();

      httpMock.expectOne('/api/organisations').flush({ id: 'org-3', name: 'New Org', createdAt: '2024-03-01' });
      tick();
      httpMock.expectOne('/api/organisations').flush(mockOrgs);

      expect(toastService.success).toHaveBeenCalledWith(jasmine.stringContaining('New Org'));
      expect(component.showCreateForm).toBe(false);
      expect(component.newOrgName).toBe('');
    }));

    it('should show formError on 400 response', fakeAsync(() => {
      component.toggleCreateForm();
      component.newOrgName = 'x';

      component.onSubmitCreate();
      tick();

      httpMock.expectOne('/api/organisations').flush(
        { violations: [{ message: 'Name too short' }] },
        { status: 400, statusText: 'Bad Request' }
      );
      tick();

      expect(component.formError).toContain('Name too short');
      expect(component.formSubmitting).toBe(false);
    }));

    it('should show permission error on 403 response', fakeAsync(() => {
      component.toggleCreateForm();
      component.newOrgName = 'Org';

      component.onSubmitCreate();
      tick();

      httpMock.expectOne('/api/organisations').flush(null, { status: 403, statusText: 'Forbidden' });
      tick();

      expect(component.formError).toContain('permission');
    }));

    it('should show generic error on unknown failure', fakeAsync(() => {
      component.toggleCreateForm();
      component.newOrgName = 'Org';

      component.onSubmitCreate();
      tick();

      httpMock.expectOne('/api/organisations').flush(null, { status: 500, statusText: 'Server Error' });
      tick();

      expect(component.formError).toContain('Failed to create');
    }));
  });

  describe('getRoleBadgeClass', () => {
    it('should return badge-primary for owner', () => {
      expect(component.getRoleBadgeClass('owner')).toBe('badge-primary');
    });

    it('should return badge-native for member', () => {
      expect(component.getRoleBadgeClass('member')).toBe('badge-native');
    });

    it('should return empty string for unknown role', () => {
      expect(component.getRoleBadgeClass('something-else')).toBe('');
    });
  });

  describe('isCurrentOrg', () => {
    it('should return true for the current org ID', () => {
      expect(component.isCurrentOrg('org-1')).toBe(true);
    });

    it('should return false for a different org ID', () => {
      expect(component.isCurrentOrg('org-2')).toBe(false);
    });

    it('should return false when authService returns undefined', () => {
      authService.getOrgId.and.returnValue(undefined);
      expect(component.isCurrentOrg('org-1')).toBe(false);
    });
  });

  describe('getCurrentOrgId', () => {
    it('should return the org ID from authService', () => {
      expect(component.getCurrentOrgId()).toBe('org-1');
    });
  });

  describe('isAdmin', () => {
    it('should return false when user is not admin', () => {
      authSpy.isAdmin.and.returnValue(false);
      expect(component.isAdmin()).toBe(false);
    });

    it('should return true when user is admin', () => {
      authSpy.isAdmin.and.returnValue(true);
      expect(component.isAdmin()).toBe(true);
    });
  });
});
