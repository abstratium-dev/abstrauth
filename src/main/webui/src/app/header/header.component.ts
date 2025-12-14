import { Component, effect, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../auth.service';
import { CommonModule } from '@angular/common';
import { Controller } from '../controller';

@Component({
    selector: 'header',
    imports: [RouterLink, RouterLinkActive, CommonModule],
    templateUrl: './header.component.html',
    styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
    private authService = inject(AuthService);
    private controller = inject(Controller);

    token!: Token;
    isSignedIn = false;

    constructor() {
        effect(() => {
            this.token = this.authService.token$();
            this.isSignedIn = this.token.isAuthenticated;
        });
    }

    ngOnInit(): void {
        this.controller.loadSignupAllowed();
    }
}
