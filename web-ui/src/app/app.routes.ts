import { Routes } from '@angular/router';
import { LoginForm, ResetPasswordForm, CreateAccountForm, ChangePasswordForm } from './shared/components';
import { AuthGuardService } from './shared/services';
import { Home } from './pages/home/home';
import { Profile } from './pages/profile/profile';
import { Tasks } from './pages/tasks/tasks';
import { CandleDownload } from './pages/data-mgmt/candle-download';
import { LocalDataExplorer } from './pages/data-mgmt/local-data-explorer';
import { SymbolMaster } from './pages/data-mgmt/symbol-master';

export const routes: Routes = [
  // Data Management
  {
    path: 'candle-download',
    component: CandleDownload,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'local-data',
    component: LocalDataExplorer,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'symbol-master',
    component: SymbolMaster,
    canActivate: [ AuthGuardService ]
  },
  // Existing
  {
    path: 'tasks',
    component: Tasks,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'profile',
    component: Profile,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'home',
    component: Home,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'login-form',
    component: LoginForm,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'reset-password',
    component: ResetPasswordForm,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'create-account',
    component: CreateAccountForm,
    canActivate: [ AuthGuardService ]
  },
  {
    path: 'change-password/:recoveryCode',
    component: ChangePasswordForm,
    canActivate: [ AuthGuardService ]
  },
  {
    path: '**',
    redirectTo: 'home'
  }
];
