import type { MockedObject } from "vitest";
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Controller } from '../controller';
import { AuditHistoryComponent } from './audit-history.component';
import { AuditEntry } from '../model.service';

describe('AuditHistoryComponent', () => {
    let component: AuditHistoryComponent;
    let fixture: ComponentFixture<AuditHistoryComponent>;
    let controller: MockedObject<Controller>;

    const mockEntries: AuditEntry[] = [
        {
            rev: 1,
            revType: 0,
            revTimestamp: 1700000000000,
            username: 'admin@example.com',
            correlationId: 'corr-123',
            changeNote: null,
            id: 'abc-123',
            email: 'user@example.com',
            name: 'Test User'
        },
        {
            rev: 2,
            revType: 1,
            revTimestamp: 1700001000000,
            username: 'admin@example.com',
            correlationId: 'corr-456',
            changeNote: 'Updated name',
            id: 'abc-123',
            email: 'user@example.com',
            name: 'Updated User'
        }
    ];

    function createComponent(entityType: string, primaryKey: string) {
        const controllerSpy = {
            getAuditHistory: vi.fn().mockName("Controller.getAuditHistory"),
            getAuditTypes: vi.fn().mockName("Controller.getAuditTypes"),
            getRelatedAuditHistory: vi.fn().mockName("Controller.getRelatedAuditHistory")
        };
        controllerSpy.getAuditHistory.mockResolvedValue(mockEntries);
        controllerSpy.getRelatedAuditHistory.mockResolvedValue([]);

        TestBed.configureTestingModule({
            imports: [AuditHistoryComponent],
            providers: [
                provideZonelessChangeDetection(),
                { provide: Controller, useValue: controllerSpy },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: {
                                get: (key: string) => {
                                    if (key === 'entityType')
                                        return entityType;
                                    if (key === 'primaryKey')
                                        return primaryKey;
                                    return null;
                                }
                            }
                        }
                    }
                }
            ]
        });

        fixture = TestBed.createComponent(AuditHistoryComponent);
        component = fixture.componentInstance;
        controller = TestBed.inject(Controller) as MockedObject<Controller>;
    }

    it('should create', () => {
        createComponent('account', 'abc-123');
        expect(component).toBeTruthy();
    });

    describe('Initialization', () => {
        it('should read entityType and primaryKey from route params', () => {
            createComponent('account', 'abc-123');
            component.ngOnInit();

            expect(component.entityType).toBe('account');
            expect(component.primaryKey).toBe('abc-123');
        });

        it('should show error when entityType is missing', () => {
            createComponent('', 'abc-123');
            component.ngOnInit();

            expect(component.error()).toBe('Missing entity type or primary key.');
            expect(component.loading()).toBe(false);
        });

        it('should show error when primaryKey is missing', () => {
            createComponent('account', '');
            component.ngOnInit();

            expect(component.error()).toBe('Missing entity type or primary key.');
            expect(component.loading()).toBe(false);
        });

        it('should call loadHistory on init with valid params', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();

            expect(controller.getAuditHistory).toHaveBeenCalledWith('account', 'abc-123');
        });
    });

    describe('Loading History', () => {
        it('should display loading state', () => {
            createComponent('account', 'abc-123');
            controller.getAuditHistory.mockReturnValue(new Promise(() => { })); // never resolves
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const loadingDiv = compiled.querySelector('.loading');
            expect(loadingDiv).toBeTruthy();
            expect(loadingDiv.textContent).toContain('Loading audit history');
        });

        it('should display entries after successful load', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const auditEntries = compiled.querySelectorAll('.audit-entry');
            expect(auditEntries.length).toBe(2);
        });

        it('should display revision type badges', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const badges = compiled.querySelectorAll('.audit-entry-meta .badge');
            expect(badges[0].textContent).toContain('INSERT');
            expect(badges[1].textContent).toContain('UPDATE');
        });

        it('should display revision numbers', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const revs = compiled.querySelectorAll('.audit-rev');
            expect(revs[0].textContent).toContain('Rev #1');
            expect(revs[1].textContent).toContain('Rev #2');
        });

        it('should display username', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const users = compiled.querySelectorAll('.audit-entry-user');
            expect(users[0].textContent).toContain('admin@example.com');
        });

        it('should display change note when present', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const notes = compiled.querySelectorAll('.audit-change-note');
            expect(notes.length).toBe(1);
            expect(notes[0].textContent).toContain('Updated name');
        });

        it('should display entity field data in table', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const firstTable = compiled.querySelector('.audit-data-table');
            expect(firstTable).toBeTruthy();
            expect(firstTable.textContent).toContain('email');
            expect(firstTable.textContent).toContain('user@example.com');
        });

        it('should display empty state when no entries', async () => {
            createComponent('account', 'abc-123');
            controller.getAuditHistory.mockResolvedValue([]);
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const infoMessage = compiled.querySelector('.info-message');
            expect(infoMessage).toBeTruthy();
            expect(infoMessage.textContent).toContain('No audit history found');
        });

        it('should display entity type badge in header', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const badge = compiled.querySelector('.badge-primary');
            expect(badge).toBeTruthy();
            expect(badge.textContent).toContain('account');
        });

        it('should display primary key in header', async () => {
            createComponent('account', 'abc-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const pk = compiled.querySelector('.audit-pk');
            expect(pk).toBeTruthy();
            expect(pk.textContent).toContain('abc-123');
        });
    });

    describe('Error Handling', () => {
        it('should display error on 403', async () => {
            createComponent('oauth_client', 'abc-123');
            controller.getAuditHistory.mockRejectedValue({ status: 403 });
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(component.error()).toBe('You do not have permission to view this audit history.');
            const compiled = fixture.nativeElement;
            const errorBox = compiled.querySelector('.error-box');
            expect(errorBox).toBeTruthy();
            expect(errorBox.textContent).toContain('permission');
        });

        it('should display error on 400 with message', async () => {
            createComponent('nonexistent', 'abc-123');
            controller.getAuditHistory.mockRejectedValue({ status: 400, error: { error: 'Unknown entity type: nonexistent' } });
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(component.error()).toBe('Unknown entity type: nonexistent');
        });

        it('should display generic error on other failures', async () => {
            createComponent('account', 'abc-123');
            controller.getAuditHistory.mockRejectedValue({ status: 500 });
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(component.error()).toBe('Failed to load audit history. Please try again.');
        });

        it('should show retry button on error', async () => {
            createComponent('account', 'abc-123');
            controller.getAuditHistory.mockRejectedValue({ status: 500 });
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const retryButton = compiled.querySelector('.error-box button');
            expect(retryButton).toBeTruthy();
            expect(retryButton.textContent).toContain('Retry');
        });

        it('should retry loading when retry button is clicked', async () => {
            createComponent('account', 'abc-123');
            controller.getAuditHistory.mockRejectedValue({ status: 500 });
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            // Now make it succeed on retry
            controller.getAuditHistory.mockResolvedValue(mockEntries);
            const retryButton = fixture.nativeElement.querySelector('.error-box button');
            retryButton.click();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(component.entries().length).toBe(2);
            expect(component.error()).toBeNull();
        });
    });

    describe('Helper Methods', () => {
        beforeEach(() => {
            createComponent('account', 'abc-123');
        });

        it('should return correct rev type name for INSERT', () => {
            expect(component.getRevTypeName(0)).toBe('INSERT');
        });

        it('should return correct rev type name for UPDATE', () => {
            expect(component.getRevTypeName(1)).toBe('UPDATE');
        });

        it('should return correct rev type name for DELETE', () => {
            expect(component.getRevTypeName(2)).toBe('DELETE');
        });

        it('should return UNKNOWN for invalid rev type', () => {
            expect(component.getRevTypeName(99)).toBe('UNKNOWN');
        });

        it('should return correct badge class for INSERT', () => {
            expect(component.getRevTypeBadgeClass(0)).toBe('badge-success');
        });

        it('should return correct badge class for UPDATE', () => {
            expect(component.getRevTypeBadgeClass(1)).toBe('badge-primary');
        });

        it('should return correct badge class for DELETE', () => {
            expect(component.getRevTypeBadgeClass(2)).toBe('badge-danger');
        });

        it('should filter meta keys from entity columns', () => {
            const entry: AuditEntry = {
                rev: 1, revType: 0, revTimestamp: 123, username: 'a', correlationId: 'b', changeNote: null,
                id: 'x', email: 'e'
            };
            const cols = component.getEntityColumns(entry);
            expect(cols).toContain('id');
            expect(cols).toContain('email');
            expect(cols).not.toContain('rev');
            expect(cols).not.toContain('revType');
            expect(cols).not.toContain('revTimestamp');
            expect(cols).not.toContain('username');
            expect(cols).not.toContain('correlationId');
            expect(cols).not.toContain('changeNote');
        });

        it('should format entity type with underscores as spaces', () => {
            expect(component.formatEntityType('oauth_client')).toBe('oauth client');
            expect(component.formatEntityType('account_role')).toBe('account role');
            expect(component.formatEntityType('account')).toBe('account');
        });

        it('should format timestamp to locale string', () => {
            const formatted = component.formatTimestamp(1700000000000);
            expect(formatted).toBeTruthy();
            // Just verify it's a non-empty string (locale-dependent)
            expect(formatted.length).toBeGreaterThan(0);
        });
    });

    describe('Related History (Account Roles)', () => {
        const mockRoleEntries: AuditEntry[] = [
            {
                rev: 3,
                revType: 0,
                revTimestamp: 1700002000000,
                username: 'admin@example.com',
                correlationId: 'corr-789',
                changeNote: null,
                id: 'role-1',
                account_id: 'abc-123',
                client_id: 'my-client',
                role: 'viewer'
            }
        ];

        it('should load related account_role history for account entity type', async () => {
            createComponent('account', 'abc-123');
            controller.getRelatedAuditHistory.mockResolvedValue(mockRoleEntries);
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(controller.getRelatedAuditHistory).toHaveBeenCalledWith('account_role', 'account', 'abc-123');
            expect(component.relatedEntries().length).toBe(1);
            expect(component.relatedEntityType()).toBe('account_role');
        });

        it('should not load related history for non-account entity types', async () => {
            createComponent('oauth_client', 'client-123');
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(controller.getRelatedAuditHistory).not.toHaveBeenCalled();
            expect(component.relatedEntityType()).toBeNull();
        });

        it('should display related entries section when loaded', async () => {
            createComponent('account', 'abc-123');
            controller.getRelatedAuditHistory.mockResolvedValue(mockRoleEntries);
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const heading = compiled.querySelector('.audit-related-heading');
            expect(heading).toBeTruthy();
            expect(heading.textContent).toContain('account role');
        });

        it('should display related entries data', async () => {
            createComponent('account', 'abc-123');
            controller.getRelatedAuditHistory.mockResolvedValue(mockRoleEntries);
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const timelines = compiled.querySelectorAll('.audit-timeline');
            // Second timeline is for related entries
            expect(timelines.length).toBe(2);
            const relatedEntries = timelines[1].querySelectorAll('.audit-entry');
            expect(relatedEntries.length).toBe(1);
        });

        it('should show empty message when no related entries', async () => {
            createComponent('account', 'abc-123');
            controller.getRelatedAuditHistory.mockResolvedValue([]);
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            const compiled = fixture.nativeElement;
            const infoMessages = compiled.querySelectorAll('.info-message');
            const relatedMessage = Array.from(infoMessages).find((el: any) => el.textContent.includes('account role'));
            expect(relatedMessage).toBeTruthy();
        });

        it('should handle related history error', async () => {
            createComponent('account', 'abc-123');
            controller.getRelatedAuditHistory.mockRejectedValue({ status: 403 });
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(component.relatedError()).toBe('You do not have permission to view related audit history.');
        });

        it('should handle generic related history error', async () => {
            createComponent('account', 'abc-123');
            controller.getRelatedAuditHistory.mockRejectedValue({ status: 500 });
            fixture.detectChanges();
            await Promise.resolve(); TestBed.flushEffects();
            await Promise.resolve(); TestBed.flushEffects();
            fixture.detectChanges();

            expect(component.relatedError()).toBe('Failed to load related audit history.');
        });
    });

    describe('Navigation', () => {
        it('should call window.history.back on goBack', () => {
            createComponent('account', 'abc-123');
            vi.spyOn(window.history, 'back').mockReturnValue(undefined);
            component.goBack();
            expect(window.history.back).toHaveBeenCalled();
        });
    });
});
