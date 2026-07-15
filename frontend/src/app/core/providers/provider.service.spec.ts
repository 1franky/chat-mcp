import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProviderService } from './provider.service';

describe('ProviderService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProviderService, provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('never adds a credential to read requests', () => {
    const service = TestBed.inject(ProviderService);
    const http = TestBed.inject(HttpTestingController);

    service.list().subscribe();

    const request = http.expectOne('/api/providers');
    expect(request.request.method).toBe('GET');
    expect(request.request.body).toBeNull();
    request.flush([]);
    http.verify();
  });
});
