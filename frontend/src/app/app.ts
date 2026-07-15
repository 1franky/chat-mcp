import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [MatButtonModule, MatToolbarModule, RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly backendUnavailable = signal(false);
  protected readonly loggingOut = signal(false);

  constructor() {
    this.auth.ensureBootstrap().subscribe({
      next: () => this.backendUnavailable.set(false),
      error: () => this.backendUnavailable.set(true),
    });
  }

  protected logout(): void {
    if (this.loggingOut()) {
      return;
    }
    this.loggingOut.set(true);
    this.auth.logout().subscribe({
      next: () => {
        this.loggingOut.set(false);
        void this.router.navigate(['/auth']);
      },
      error: () => this.loggingOut.set(false),
    });
  }
}
