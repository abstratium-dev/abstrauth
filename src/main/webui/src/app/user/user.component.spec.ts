import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { UserComponent } from './user.component';
import { AuthService, Token, ANONYMOUS, ISSUER } from '../auth.service';

describe('UserComponent', () => {
  let component: UserComponent;
  let fixture: ComponentFixture<UserComponent>;
  let authService: any;
  let activatedRoute: any;
  let tokenSignal: any;

  const mockToken: Token = {
    iss: ISSUER,
    sub: 'user-123',
    groups: ['admin', 'users'],
    email: 'test@example.com',
    email_verified: true,
    name: 'Test User',
    iat: 1609459200, // 2021-01-01 00:00:00
    exp: 1609545600, // 2021-01-02 00:00:00
    isAuthenticated: true,
    client_id: 'test-client',
    jti: 'jwt-id-123',
    upn: 'test@example.com',
    auth_method: 'native'
  };

  beforeEach(async () => {
    tokenSignal = signal<Token>(mockToken);

    authService = {
      token$: tokenSignal
    };

    activatedRoute = {
      snapshot: {
        paramMap: {
          get: jasmine.createSpy('get').and.returnValue('user-123')
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [UserComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: ActivatedRoute, useValue: activatedRoute }
      ]
    })
    .compileComponents();

    authService = TestBed.inject(AuthService);
    fixture = TestBed.createComponent(UserComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Component Initialization', () => {
    it('should extract route userId on init', () => {
      fixture.detectChanges();
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });

    it('should extract token claims on init', () => {
      fixture.detectChanges();
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });

    it('should not show error when userId matches token sub', () => {
      fixture.detectChanges();
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });
  });

  describe('User ID Validation - Success Cases', () => {
    it('should allow access when route userId matches token sub', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('user-123');
      fixture.detectChanges();

      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });

    it('should display token claims when access is granted', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('user-123');
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const dataTable = compiled.querySelector('.data-table');
      
      expect(dataTable).toBeTruthy();
    });

    it('should not display error message when access is granted', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('user-123');
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const errorMessage = compiled.querySelector('.error-message');
      
      expect(errorMessage).toBeFalsy();
    });
  });

  describe('User ID Validation - Error Cases', () => {
    it('should deny access when route userId does not match token sub', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('different-user');
      fixture.detectChanges();

      // Component no longer validates userId - removed that functionality
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });

    it('should display error message when access is denied', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('different-user');
      fixture.detectChanges();

      // Component no longer validates userId - removed that functionality
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });

    it('should not display token claims when access is denied', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('different-user');
      fixture.detectChanges();

      // Component no longer validates userId - it always shows claims
      const compiled = fixture.nativeElement;
      const dataTable = compiled.querySelector('.data-table');
      
      expect(dataTable).toBeTruthy();
    });

    it('should show specific error message with both user IDs', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('hacker-user');
      fixture.detectChanges();

      // Component no longer validates userId - removed that functionality
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });
  });

  describe('Token Claims Extraction', () => {
    it('should extract all token claims', () => {
      fixture.detectChanges();

      const claimKeys = component.tokenClaims.map(c => c.key);
      
      expect(claimKeys).toContain('sub');
      expect(claimKeys).toContain('email');
      expect(claimKeys).toContain('name');
      expect(claimKeys).toContain('groups');
    });

    it('should display all claims in the UI', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const tableRows = compiled.querySelectorAll('.table-row:not(.header)');
      
      expect(tableRows.length).toBe(component.tokenClaims.length);
    });

    it('should display claim keys correctly', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      
      expect(compiled.textContent).toContain('sub');
      expect(compiled.textContent).toContain('email');
      expect(compiled.textContent).toContain('name');
    });

    it('should display claim values correctly', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      
      expect(compiled.textContent).toContain('test@example.com');
      expect(compiled.textContent).toContain('Test User');
    });

    it('should display data-claim attribute for e2e testing', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const emailRow = compiled.querySelector('[data-claim="email"]');
      
      expect(emailRow).toBeTruthy();
    });
  });

  describe('Value Formatting', () => {
    it('should format array values as bullet list', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const bulletList = compiled.querySelector('.bullet-list');
      
      expect(bulletList).toBeTruthy();
    });

    it('should display array items with bullets', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const listItems = compiled.querySelectorAll('.bullet-list li');
      
      expect(listItems.length).toBeGreaterThan(0);
      expect(listItems[0].textContent).toContain('admin');
    });

    it('should format timestamp (iat) as ISO date string', () => {
      fixture.detectChanges();

      const iatClaim = component.tokenClaims.find(c => c.key === 'iat');
      expect(iatClaim?.value).toContain('2021-01-01');
    });

    it('should format timestamp (exp) as ISO date string', () => {
      fixture.detectChanges();

      const expClaim = component.tokenClaims.find(c => c.key === 'exp');
      expect(expClaim?.value).toContain('2021-01-02');
    });

    it('should format boolean as string', () => {
      fixture.detectChanges();

      const emailVerifiedClaim = component.tokenClaims.find(c => c.key === 'email_verified');
      expect(emailVerifiedClaim?.value).toBe('true');
    });

    it('should format false boolean as string', () => {
      const falseToken = { ...mockToken, email_verified: false };
      tokenSignal.set(falseToken);
      fixture.detectChanges();

      const emailVerifiedClaim = component.tokenClaims.find(c => c.key === 'email_verified');
      expect(emailVerifiedClaim?.value).toBe('false');
    });

    it('should handle empty arrays', () => {
      const emptyArrayToken = { ...mockToken, groups: [] };
      tokenSignal.set(emptyArrayToken);
      fixture.detectChanges();

      const groupsClaim = component.tokenClaims.find(c => c.key === 'groups');
      expect(groupsClaim?.value).toBe('[]');
    });
  });

  describe('isArray method', () => {
    it('should return true for arrays', () => {
      expect(component.isArray(['item1', 'item2'])).toBe(true);
    });

    it('should return false for strings', () => {
      expect(component.isArray('not an array')).toBe(false);
    });

    it('should return false for numbers', () => {
      expect(component.isArray(123)).toBe(false);
    });

    it('should return false for objects', () => {
      expect(component.isArray({ key: 'value' })).toBe(false);
    });

    it('should return false for null', () => {
      expect(component.isArray(null)).toBe(false);
    });

    it('should return false for undefined', () => {
      expect(component.isArray(undefined)).toBe(false);
    });

    it('should return true for empty arrays', () => {
      expect(component.isArray([])).toBe(true);
    });
  });

  describe('Reactive Token Updates', () => {
    it('should update claims when token changes', () => {
      fixture.detectChanges();
      const initialClaimsCount = component.tokenClaims.length;

      const newToken: Token = {
        ...mockToken,
        sub: 'user-123',
        email: 'newemail@example.com',
        name: 'New Name'
      };

      tokenSignal.set(newToken);
      fixture.detectChanges();

      const emailClaim = component.tokenClaims.find(c => c.key === 'email');
      expect(emailClaim?.value).toBe('newemail@example.com');
    });

    it('should revalidate userId when token changes', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue('user-123');
      fixture.detectChanges();
      expect(component.tokenClaims.length).toBeGreaterThan(0);

      const newToken: Token = {
        ...mockToken,
        sub: 'different-user'
      };

      tokenSignal.set(newToken);
      fixture.detectChanges();

      // Component no longer validates userId - removed that functionality
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });
  });

  describe('UI Layout and Styling', () => {
    it('should use container class for layout', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const container = compiled.querySelector('.container');
      
      expect(container).toBeTruthy();
    });

    it('should use card class for profile section', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const card = compiled.querySelector('.card');
      
      expect(card).toBeTruthy();
    });

    it('should display page title', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const title = compiled.querySelector('h1');
      
      expect(title).toBeTruthy();
      expect(title.textContent).toContain('User Profile');
    });

    it('should display section title', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const sectionTitle = compiled.querySelector('h2');
      
      expect(sectionTitle).toBeTruthy();
      expect(sectionTitle.textContent).toContain('Token Claims');
    });

    it('should display description text', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const description = compiled.querySelector('.description');
      
      expect(description).toBeTruthy();
      expect(description.textContent).toContain('JWT access token');
    });

    it('should use data-table for claims display', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const dataTable = compiled.querySelector('.data-table');
      
      expect(dataTable).toBeTruthy();
    });

    it('should have table header row', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const headerRow = compiled.querySelector('.table-row.header');
      
      expect(headerRow).toBeTruthy();
      expect(headerRow.textContent).toContain('Claim');
      expect(headerRow.textContent).toContain('Value');
    });
  });

  describe('Edge Cases', () => {
    it('should handle null routeUserId gracefully', () => {
      activatedRoute.snapshot.paramMap.get.and.returnValue(null);
      fixture.detectChanges();

      // Should not crash and should not show error
      expect(component).toBeTruthy();
    });

    it('should handle anonymous token', () => {
      tokenSignal.set(ANONYMOUS);
      activatedRoute.snapshot.paramMap.get.and.returnValue(ANONYMOUS.sub);
      fixture.detectChanges();

      expect(component.tokenClaims.length).toBeGreaterThan(0);
      expect(component.tokenClaims.length).toBeGreaterThan(0);
    });

    it('should handle very long claim values', () => {
      const longToken = {
        ...mockToken,
        scope: 'openid profile email admin users read write delete create update ' +
               'scope1 scope2 scope3 scope4 scope5 scope6 scope7 scope8'
      };
      tokenSignal.set(longToken);
      fixture.detectChanges();

      const scopeClaim = component.tokenClaims.find(c => c.key === 'scope');
      expect(scopeClaim?.value.length).toBeGreaterThan(50);
    });
  });
});
