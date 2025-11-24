import { Routes } from '@angular/router';
import { UserComponent } from './user/user.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { HomeComponent } from './home/home.component';
import { SigninComponent } from './signin/signin.component';
import { AuthCallbackComponent } from './auth-callback/auth-callback.component';
import { SignupComponent } from './signup/signup.component';
import { AuthorizeComponent } from './authorize/authorize.component';

export const routes: Routes = [
  { path: '',         component: HomeComponent },
  { path: 'authorize',   component: AuthorizeComponent },
  { path: 'signin/:requestId',   component: SigninComponent },
  { path: 'signup',   component: SignupComponent },
  { path: 'auth-callback',   component: AuthCallbackComponent },
  { path: 'user/:id', component: UserComponent },
  { path: '**',       component: NotFoundComponent }
];
