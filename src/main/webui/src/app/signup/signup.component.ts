import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ModelService } from '../model.service';

@Component({
  selector: 'signup',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './signup.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './signup.component.scss',
})
export class SignupComponent {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private router = inject(Router);
  private modelService = inject(ModelService);

  get requestId(): string {
    return this.modelService.signInRequestId$();
  }

  signupForm: FormGroup;

  private message$ = signal<string>('');
  private messageType$ = signal<'success' | 'error' | ''>('');
  private isSubmitting$ = signal<boolean>(false);
  private organisationNameManuallyEdited$ = signal<boolean>(false);

  get message(): string { return this.message$(); }
  set message(v: string) { this.message$.set(v); }
  get messageType(): 'success' | 'error' | '' { return this.messageType$(); }
  set messageType(v: 'success' | 'error' | '') { this.messageType$.set(v); }
  get isSubmitting(): boolean { return this.isSubmitting$(); }
  set isSubmitting(v: boolean) { this.isSubmitting$.set(v); }
  get organisationNameManuallyEdited(): boolean { return this.organisationNameManuallyEdited$(); }
  set organisationNameManuallyEdited(v: boolean) { this.organisationNameManuallyEdited$.set(v); }

  constructor() {
    this.signupForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      name: [''],
      organisationName: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(8)]],
      password2: ['', [Validators.required, Validators.minLength(8)]],
    });

    const nameValue = toSignal(this.signupForm.get('name')!.valueChanges, { initialValue: '' });

    // Auto-populate organisationName when name changes, unless manually edited
    effect(() => {
      const name = nameValue();
      if (!this.organisationNameManuallyEdited$()) {
        const orgName = name ? `${name}'s Organisation` : '';
        this.signupForm.get('organisationName')!.setValue(orgName, { emitEvent: false });
      }
    });
  }

  onOrganisationNameChange(): void {
    this.organisationNameManuallyEdited$.set(true);
  }

  signup() {
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }

    if (this.signupForm.value.password !== this.signupForm.value.password2) {
      this.messageType$.set('error');
      this.message$.set('Passwords do not match');
      return;
    }

    this.isSubmitting$.set(true);
    this.message$.set('');
    this.messageType$.set('');

    const formData = new URLSearchParams();
    formData.append('email', this.signupForm.value.email);
    formData.append('name', this.signupForm.value.name);
    formData.append('username', this.signupForm.value.email);
    formData.append('password', this.signupForm.value.password);
    formData.append('organisationName', this.signupForm.value.organisationName);

    this.http.post<any>('/api/signup', formData.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    }).subscribe({
      next: (response) => {
        this.messageType$.set('success');
        this.message$.set(`Account created successfully! Your account ID is: ${response.id}`);
        this.isSubmitting$.set(false);

        // Store username and password for signin page
        const username = this.signupForm.value.email; // username is currently always equal to the email
        const password = this.signupForm.value.password;
        this.modelService.setSignUpUsername(username);
        this.modelService.setSignUpPassword(password);

        this.signupForm.reset();

        // Redirect to signin page
        // If there's no requestId (e.g., user navigated directly to /signup),
        // redirect to home which will create a new signin request
        if (this.requestId) {
          this.router.navigate(['/signin', this.requestId]);
        } else {
          this.router.navigate(['/']);
        }
      },
      error: (error) => {
        this.messageType$.set('error');
        this.message$.set(error.error?.error_description || error.error?.error || 'Signing up failed');
        this.isSubmitting$.set(false);
      }
    });
  }
}
