import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { apiErrorMessage } from '../../core/http/api-error';
import { AuthService } from '../../core/auth/auth.service';

type AuthMode = 'login' | 'register';

@Component({
  selector: 'app-auth-page',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  templateUrl: './auth-page.html',
  styleUrl: './auth-page.scss',
})
export class AuthPage {
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly loadingBootstrap = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error = signal('');
  protected readonly mode = signal<AuthMode>('login');
  protected readonly bootstrap = this.auth.bootstrap;

  protected readonly form = this.formBuilder.nonNullable.group({
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
  });

  constructor() {
    this.auth.ensureBootstrap().subscribe({
      next: (status) => {
        this.mode.set(status.bootstrapRequired ? 'register' : 'login');
        this.loadingBootstrap.set(false);
      },
      error: (error) => {
        this.error.set(apiErrorMessage(error));
        this.loadingBootstrap.set(false);
      },
    });
  }

  protected selectMode(mode: AuthMode): void {
    if (this.bootstrap()?.bootstrapRequired) {
      return;
    }
    this.error.set('');
    this.mode.set(mode);
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    const controls = this.form.controls;
    controls.email.markAsTouched();
    controls.password.markAsTouched();
    if (this.mode() === 'register') {
      controls.displayName.markAsTouched();
    }
    if (
      controls.email.invalid ||
      controls.password.invalid ||
      (this.mode() === 'register' && controls.displayName.invalid)
    ) {
      return;
    }

    this.submitting.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    const operation =
      this.mode() === 'register'
        ? this.auth.register(value)
        : this.auth.login({ email: value.email, password: value.password });
    operation.subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/home']);
      },
      error: (error) => {
        this.submitting.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
  }
}
