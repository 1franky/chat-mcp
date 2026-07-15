import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminUsersPage } from './admin-users-page';

describe('AdminUsersPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUsersPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('renders the paginated administrative user list', () => {
    const fixture = TestBed.createComponent(AdminUsersPage);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/admin/users?page=0&size=20').flush({
      users: [
        {
          id: '6dc26004-382d-4f0a-a838-8614e32da62b',
          email: 'admin@example.test',
          displayName: 'Test Admin',
          role: 'ADMIN',
          active: true,
          createdAt: '2026-07-15T00:00:00Z',
          updatedAt: '2026-07-15T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Test Admin');
    expect(fixture.nativeElement.textContent).toContain('admin@example.test');
    expect(fixture.nativeElement.textContent).toContain('Cambiar a USER');
    http.verify();
  });
});
