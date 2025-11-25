import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthorizeComponent } from './authorize.component';

describe('AuthorizeComponent', () => {
  let component: AuthorizeComponent;
  let fixture: ComponentFixture<AuthorizeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthorizeComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthorizeComponent);
    component = fixture.componentInstance;
    
    // Spy on authorize to prevent navigation
    spyOn(component, 'authorize').and.returnValue(Promise.resolve());
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not navigate during test', () => {
    fixture.detectChanges();
    expect(component.authorize).toHaveBeenCalled();
  });
});
