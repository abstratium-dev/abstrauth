import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { ConfirmDialogService } from './confirm-dialog.service';

describe('ConfirmDialogComponent', () => {
  let component: ConfirmDialogComponent;
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let service: ConfirmDialogService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;
    service = TestBed.inject(ConfirmDialogService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not display dialog initially', () => {
    const compiled = fixture.nativeElement;
    const overlay = compiled.querySelector('.dialog-overlay');
    expect(overlay).toBeFalsy();
  });

  it('should display dialog when opened', () => {
    service.confirm({
      title: 'Test Title',
      message: 'Test Message'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const overlay = compiled.querySelector('.dialog-overlay');
    expect(overlay).toBeTruthy();
  });

  it('should display title and message', () => {
    service.confirm({
      title: 'Delete Account',
      message: 'Are you sure?'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Delete Account');
    expect(compiled.textContent).toContain('Are you sure?');
  });

  it('should call confirm when confirm button clicked', () => {
    spyOn(component, 'confirm');
    
    service.confirm({
      title: 'Test',
      message: 'Test'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const confirmBtn = compiled.querySelector('.btn-danger');
    confirmBtn.click();

    expect(component.confirm).toHaveBeenCalled();
  });

  it('should call cancel when cancel button clicked', () => {
    spyOn(component, 'cancel');
    
    service.confirm({
      title: 'Test',
      message: 'Test'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const cancelBtn = compiled.querySelector('.btn-secondary');
    cancelBtn.click();

    expect(component.cancel).toHaveBeenCalled();
  });

  it('should close dialog when overlay clicked', () => {
    spyOn(component, 'cancel');
    
    service.confirm({
      title: 'Test',
      message: 'Test'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const overlay = compiled.querySelector('.dialog-overlay');
    overlay.click();

    expect(component.cancel).toHaveBeenCalled();
  });
});
