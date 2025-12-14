import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Controller } from '../controller';
import { ToastService } from '../shared/toast/toast.service';

interface InviteData {
  authProvider: string;
  email: string;
  password?: string;
}

@Component({
  selector: 'app-change-password',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss'
})
export class ChangePasswordComponent implements OnInit {
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private controller = inject(Controller);
  private toastService = inject(ToastService);

  passwordForm: FormGroup;
  isSubmitting = false;
  errorMessage: string | null = null;
  inviteData: InviteData | null = null;

  constructor() {
    this.passwordForm = this.fb.group({
      oldPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    // Check if password change is required
    const requirePasswordChange = sessionStorage.getItem('requirePasswordChange');
    if (!requirePasswordChange) {
      // Redirect to home if not required
      this.router.navigate(['/']);
      return;
    }

    // Get invite data to prefill old password
    const inviteDataStr = sessionStorage.getItem('inviteData');
    if (inviteDataStr) {
      try {
        this.inviteData = JSON.parse(inviteDataStr);
        if (this.inviteData?.password) {
          this.passwordForm.patchValue({
            oldPassword: this.inviteData.password
          });
        }
      } catch (err) {
        console.error('Error parsing invite data:', err);
      }
    }
  }

  async onSubmit(): Promise<void> {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { oldPassword, newPassword, confirmPassword } = this.passwordForm.value;

    // Check if new passwords match
    if (newPassword !== confirmPassword) {
      this.errorMessage = 'New passwords do not match';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = null;

    try {
      await this.controller.resetPassword(oldPassword, newPassword);
      
      // Clear session storage
      sessionStorage.removeItem('inviteData');
      sessionStorage.removeItem('requirePasswordChange');
      
      this.toastService.success('Password changed successfully!');
      
      // Redirect to home
      this.router.navigate(['/']);
    } catch (err: any) {
      if (err.status === 400) {
        this.errorMessage = err.error?.error || 'Old password is incorrect';
      } else {
        this.errorMessage = 'Failed to change password. Please try again.';
      }
    } finally {
      this.isSubmitting = false;
    }
  }

  get newPasswordInvalid(): boolean {
    const control = this.passwordForm.get('newPassword');
    return !!(control && control.invalid && control.touched);
  }

  get confirmPasswordInvalid(): boolean {
    const control = this.passwordForm.get('confirmPassword');
    const newPassword = this.passwordForm.get('newPassword')?.value;
    const confirmPassword = control?.value;
    return !!(control && control.touched && newPassword !== confirmPassword);
  }
}
