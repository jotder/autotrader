import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { DxButtonModule } from 'devextreme-angular/ui/button';
import { DxToolbarModule } from 'devextreme-angular/ui/toolbar';

import { AuthService, IUser } from '../../services';
import { UserPanel } from '../user-panel/user-panel';
import { ThemeSwitcher } from '../theme-switcher/theme-switcher';
import { BrokerConnector } from '../broker-connector/broker-connector';

@Component({
  selector: 'app-header',
  templateUrl: 'header.html',
  styleUrls: ['./header.scss'],
  standalone: true,
  imports: [
    CommonModule,
    DxButtonModule,
    UserPanel,
    DxToolbarModule,
    ThemeSwitcher,
    BrokerConnector,
  ]
})

export class Header implements OnInit {
  @Output()
  menuToggle = new EventEmitter<boolean>();

  @Input()
  menuToggleEnabled = false;

  @Input()
  title!: string;

  user: IUser | null = { email: '' };

  userMenuItems = [
    {
    text: 'Profile',
    icon: 'user',
    onClick: () => {
      this.router.navigate(['/profile']);
    }
  },
  {
    text: 'Logout',
    icon: 'runner',
    onClick: () => {
      this.authService.logOut();
    }
  }];

  constructor(private authService: AuthService, private router: Router) { }

  ngOnInit() {
    this.authService.getUser().then((e) => this.user = e.data);
  }

  toggleMenu = () => {
    this.menuToggle.emit();
  }
}
