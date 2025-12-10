import { Component, effect, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../auth.service';
import { CommonModule } from '@angular/common';
import { Controller } from '../controller';
import { ModelService } from '../model.service';

@Component({
    selector: 'header',
    imports: [RouterLink, RouterLinkActive, CommonModule],
    templateUrl: './header.component.html',
    styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
    private authService = inject(AuthService);
    private controller = inject(Controller);
    private modelService = inject(ModelService);

    token!: Token;
    isSignedIn = false;

    constructor() {
        effect(() => {
            this.token = this.authService.token$();
            this.isSignedIn = this.token.isAuthenticated;
            console.info("header isSignedIn", this.isSignedIn);
            console.info("header roles: ", this.token.groups);
        });
    }

    ngOnInit(): void {
        this.controller.loadSignupAllowed();
    }
}
