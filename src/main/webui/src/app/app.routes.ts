import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthorizeComponent } from './authorize/authorize.component';
import { HomeComponent } from './home/home.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { SigninAfterInviteComponent } from './signin-after-invite/signin-after-invite.component';
import { SigninComponent } from './signin/signin.component';
import { SignupComponent } from './signup/signup.component';

export const routes: Routes = [
  { path: '',         component: HomeComponent },
  { path: 'authorize',   component: AuthorizeComponent },
  { path: 'signin/:requestId',   component: SigninComponent },
  { path: 'signin-after-invite',   component: SigninAfterInviteComponent },
  { path: 'signup',   component: SignupComponent },

  // /////////////////////////////////
  // lazily loaded routes follow:
  // /////////////////////////////////
  { path: 'change-password',   loadComponent: () => import('./change-password/change-password.component').then(m => m.ChangePasswordComponent), canActivate: [authGuard] },
  { path: 'org-selection/:requestId', loadComponent: () => import('./org-selection/org-selection.component').then(m => m.OrgSelectionComponent) },
  { path: 'clients',  loadComponent: () => import('./clients/clients.component').then(m => m.ClientsComponent), canActivate: [authGuard] },
  { path: 'accounts', loadComponent: () => import('./accounts/accounts.component').then(m => m.AccountsComponent), canActivate: [authGuard] },
  { path: 'organisations', loadComponent: () => import('./organisations/organisations.component').then(m => m.OrganisationsComponent), canActivate: [authGuard] },
  { path: 'organisations/:orgId', loadComponent: () => import('./organisation-detail/organisation-detail.component').then(m => m.OrganisationDetailComponent), canActivate: [authGuard] },
  { path: 'user', loadComponent: () => import('./user/user.component').then(m => m.UserComponent), canActivate: [authGuard] },
  { path: 'legal', loadComponent: () => import('./legal/legal.component').then(m => m.LegalComponent) },
  { path: 'audit/:entityType/:primaryKey', loadComponent: () => import('./audit-history/audit-history.component').then(m => m.AuditHistoryComponent), canActivate: [authGuard] },
  { path: '**',       component: NotFoundComponent }
];
