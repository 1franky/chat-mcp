import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UserAccount, UserPage, UserRole } from '../../core/auth/auth.models';
import { apiErrorMessage } from '../../core/http/api-error';

@Component({
  selector: 'app-admin-users-page',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  templateUrl: './admin-users-page.html',
  styleUrl: './admin-users-page.scss',
})
export class AdminUsersPage {
  private readonly http = inject(HttpClient);
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly page = signal<UserPage | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly deactivateConfirmation = signal<string | null>(null);

  protected readonly createForm = this.formBuilder.nonNullable.group({
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
  });

  constructor() {
    this.loadPage(0);
  }

  protected loadPage(page: number): void {
    if (page < 0) {
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.http.get<UserPage>(`/api/admin/users?page=${page}&size=20`).subscribe({
      next: (result) => {
        this.page.set(result);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(apiErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected createUser(): void {
    this.createForm.markAllAsTouched();
    if (this.createForm.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.http.post<UserAccount>('/api/admin/users', this.createForm.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.createForm.reset();
        this.loadPage(0);
      },
      error: (error) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  protected changeRole(user: UserAccount): void {
    const role: UserRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
    this.error.set('');
    this.http.patch<UserAccount>(`/api/admin/users/${user.id}/role`, { role }).subscribe({
      next: () => this.afterUserMutation(user.id),
      error: (error) => this.error.set(apiErrorMessage(error)),
    });
  }

  protected requestDeactivation(user: UserAccount): void {
    if (this.deactivateConfirmation() !== user.id) {
      this.deactivateConfirmation.set(user.id);
      return;
    }
    this.error.set('');
    this.http.delete<void>(`/api/admin/users/${user.id}`).subscribe({
      next: () => {
        this.deactivateConfirmation.set(null);
        this.afterUserMutation(user.id);
      },
      error: (error) => {
        this.deactivateConfirmation.set(null);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  protected hasNextPage(): boolean {
    const current = this.page();
    return Boolean(current && (current.page + 1) * current.size < current.totalElements);
  }

  private afterUserMutation(targetId: string): void {
    if (targetId === this.auth.currentUser()?.id) {
      this.auth.refreshBootstrap().subscribe({
        next: (status) => {
          if (!status.authenticated || status.user?.role !== 'ADMIN') {
            void this.router.navigate([status.authenticated ? '/home' : '/auth']);
          } else {
            this.loadPage(this.page()?.page ?? 0);
          }
        },
        error: () => void this.router.navigate(['/auth']),
      });
      return;
    }
    this.loadPage(this.page()?.page ?? 0);
  }
}
