import { Routes } from '@angular/router';
import { UserComponent } from './user/user.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { HomeComponent } from './home/home.component';
import { SigninComponent } from './signin/signin.component';
import { SignoutComponent } from './signout/signout.component';
import { AuthCallbackComponent } from './auth-callback/auth-callback.component';
import { SignupComponent } from './signup/signup.component';
import { AuthorizeComponent } from './authorize/authorize.component';
import { ClientsComponent } from './clients/clients.component';
import { authGuard } from './auth.guard';

export const routes: Routes = [
  { path: '',         component: HomeComponent, canActivate: [authGuard] },
  { path: 'authorize',   component: AuthorizeComponent },
  { path: 'signin/:requestId',   component: SigninComponent },
  { path: 'signout',   component: SignoutComponent },
  { path: 'signup',   component: SignupComponent },
  { path: 'auth-callback',   component: AuthCallbackComponent },
  { path: 'clients',  component: ClientsComponent, canActivate: [authGuard] },
  { path: 'user/:id', component: UserComponent, canActivate: [authGuard] },
  { path: '**',       component: NotFoundComponent }
];
