import { Routes } from '@angular/router';
import { UserComponent } from './user/user.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { HomeComponent } from './home/home.component';

export const routes: Routes = [
  { path: '',         component: HomeComponent },
  { path: 'user/:id', component: UserComponent },
  { path: '**',       component: NotFoundComponent }
];
