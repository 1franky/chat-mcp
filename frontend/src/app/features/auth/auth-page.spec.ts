import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthPage } from './auth-page';

describe('AuthPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('identifies the atomic first-administrator flow', () => {
    const fixture = TestBed.createComponent(AuthPage);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/auth/bootstrap').flush({
      bootstrapRequired: true,
      registrationOpen: true,
      authenticated: false,
      user: null,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Crea el primer administrador');
    expect(fixture.nativeElement.textContent).toContain('Esta cuenta sera ADMIN');
    http.verify();
  });
});
