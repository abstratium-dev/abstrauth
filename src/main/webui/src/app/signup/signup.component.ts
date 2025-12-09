import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, effect, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Controller } from '../controller';
import { ModelService } from '../model.service';
import { AuthService } from '../auth.service';

@Component({
  selector: 'signup',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.scss',
})
export class SignupComponent {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private router = inject(Router);
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private authService = inject(AuthService);

  requestId = "";

  signupForm: FormGroup;
  message: string = '';
  messageType: 'success' | 'error' | '' = '';
  isSubmitting: boolean = false;

  constructor(
  ) {
    effect(() => {
      this.requestId = this.modelService.signInRequestId$();
    });

    this.signupForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      name: [''],
      username: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  signup() {
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.message = '';
    this.messageType = '';

    const formData = new URLSearchParams();
    formData.append('email', this.signupForm.value.email);
    formData.append('name', this.signupForm.value.name);
    formData.append('username', this.signupForm.value.username);
    formData.append('password', this.signupForm.value.password);

    this.http.post<any>('/api/signup', formData.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    }).subscribe({
      next: (response) => {
        this.messageType = 'success';
        this.message = `Account created successfully! Your account ID is: ${response.id}`;
        this.isSubmitting = false;
        
        // Store username and password for signin page
        const username = this.signupForm.value.username;
        const password = this.signupForm.value.password;
        this.modelService.setSignUpUsername(username);
        this.modelService.setSignUpPassword(password);
        
        this.signupForm.reset();
        
        // Redirect to signin page
        this.router.navigate(['/signin', this.requestId]);
      },
      error: (error) => {
        this.messageType = 'error';
        this.message = error.error?.error_description || error.error?.error || 'Signing up failed';
        this.isSubmitting = false;
      }
    });
  }
}
