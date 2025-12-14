import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';

interface InviteData {
  authProvider: string;
  email: string;
  password?: string;
}

@Component({
  selector: 'app-signin-after-invite',
  imports: [CommonModule],
  templateUrl: './signin-after-invite.component.html',
  styleUrl: './signin-after-invite.component.scss'
})
export class SigninAfterInviteComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  error: string | null = null;
  loading = true;

  ngOnInit(): void {
    // Get token from query params
    const token = this.route.snapshot.queryParamMap.get('token');
    
    if (!token) {
      this.error = 'Invalid invite link: No token provided';
      this.loading = false;
      return;
    }

    try {
      // Decode base64 token
      const decodedString = atob(token);
      const inviteData: InviteData = JSON.parse(decodedString);

      // Validate invite data
      if (!inviteData.authProvider || !inviteData.email) {
        this.error = 'Invalid invite link: Missing required data';
        this.loading = false;
        return;
      }

      // Store in session storage
      sessionStorage.setItem('inviteData', JSON.stringify(inviteData));

      // Redirect to authorize route to initiate sign-in
      this.router.navigate(['/authorize']);
    } catch (err) {
      console.error('Error processing invite token:', err);
      this.error = 'Invalid invite link: Could not process token';
      this.loading = false;
    }
  }
}
