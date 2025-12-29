import { Routes } from '@angular/router';
import { UserComponent } from './user/user.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { HomeComponent } from './home/home.component';
import { SigninComponent } from './signin/signin.component';
import { SignupComponent } from './signup/signup.component';
import { AuthorizeComponent } from './authorize/authorize.component';
import { ClientsComponent } from './clients/clients.component';
import { AccountsComponent } from './accounts/accounts.component';
import { SigninAfterInviteComponent } from './signin-after-invite/signin-after-invite.component';
import { ChangePasswordComponent } from './change-password/change-password.component';
import { authGuard } from './auth.guard';

export const routes: Routes = [
  { path: '',         component: HomeComponent, canActivate: [authGuard] },
  { path: 'authorize',   component: AuthorizeComponent },
  { path: 'signin/:requestId',   component: SigninComponent },
  { path: 'signin-after-invite',   component: SigninAfterInviteComponent },
  { path: 'signup',   component: SignupComponent },
  { path: 'change-password',   component: ChangePasswordComponent, canActivate: [authGuard] },
  { path: 'clients',  component: ClientsComponent, canActivate: [authGuard] },
  { path: 'accounts', component: AccountsComponent, canActivate: [authGuard] },
  { path: 'user', component: UserComponent, canActivate: [authGuard] },
  { path: '**',       component: NotFoundComponent }
];
