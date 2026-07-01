import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { ConfirmDialogService } from './confirm-dialog.service';

describe('ConfirmDialogComponent', () => {
    let component: ConfirmDialogComponent;
    let fixture: ComponentFixture<ConfirmDialogComponent>;
    let service: ConfirmDialogService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ConfirmDialogComponent],
            providers: [provideZonelessChangeDetection()]
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
        vi.spyOn(component, 'confirm').mockReturnValue(undefined);

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
        vi.spyOn(component, 'cancel').mockReturnValue(undefined);

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
        vi.spyOn(component, 'cancel').mockReturnValue(undefined);

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

    describe('requiredPhrase', () => {
        it('should show phrase input when requiredPhrase is set', () => {
            service.confirm({
                title: 'Delete',
                message: 'Are you sure?',
                requiredPhrase: 'myemail@example.com'
            });
            fixture.detectChanges();

            const input = fixture.nativeElement.querySelector('[data-testid="confirm-phrase-input"]');
            expect(input).toBeTruthy();
            expect(fixture.nativeElement.textContent).toContain('myemail@example.com');
        });

        it('should not show phrase input when requiredPhrase is not set', () => {
            service.confirm({ title: 'Delete', message: 'Are you sure?' });
            fixture.detectChanges();

            const input = fixture.nativeElement.querySelector('[data-testid="confirm-phrase-input"]');
            expect(input).toBeFalsy();
        });

        it('should disable confirm button until phrase matches', () => {
            service.confirm({
                title: 'Delete',
                message: 'Are you sure?',
                requiredPhrase: 'confirm-me'
            });
            fixture.detectChanges();

            const confirmBtn = fixture.nativeElement.querySelector('.btn-danger');
            expect(confirmBtn.disabled).toBe(true);

            service.updateTypedPhrase('confirm-me');
            fixture.detectChanges();
            expect(confirmBtn.disabled).toBe(false);
        });

        it('should not fire handleConfirm when phrase does not match', () => {
            vi.spyOn(service, 'handleConfirm').mockReturnValue(undefined);
            service.confirm({
                title: 'Delete',
                message: 'Are you sure?',
                requiredPhrase: 'confirm-me'
            });
            fixture.detectChanges();

            component.confirm();

            expect(service.handleConfirm).not.toHaveBeenCalled();
        });

        it('should fire handleConfirm when phrase matches', () => {
            vi.spyOn(service, 'handleConfirm').mockReturnValue(undefined);
            service.confirm({
                title: 'Delete',
                message: 'Are you sure?',
                requiredPhrase: 'confirm-me'
            });
            service.updateTypedPhrase('confirm-me');
            fixture.detectChanges();

            component.confirm();

            expect(service.handleConfirm).toHaveBeenCalled();
        });
    });
});
