import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ProviderConnection,
  ProviderModel,
  ProviderTestResult,
  SaveProviderRequest,
} from './provider.models';

@Injectable({ providedIn: 'root' })
export class ProviderService {
  private readonly http = inject(HttpClient);

  list(): Observable<ProviderConnection[]> {
    return this.http.get<ProviderConnection[]>('/api/providers');
  }

  create(request: SaveProviderRequest): Observable<ProviderConnection> {
    return this.http.post<ProviderConnection>('/api/providers', request);
  }

  update(id: string, request: SaveProviderRequest): Observable<ProviderConnection> {
    return this.http.put<ProviderConnection>(`/api/providers/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/providers/${id}`);
  }

  test(id: string): Observable<ProviderTestResult> {
    return this.http.post<ProviderTestResult>(`/api/providers/${id}/test`, {});
  }

  synchronizeModels(id: string): Observable<ProviderModel[]> {
    return this.http.post<ProviderModel[]>(`/api/providers/${id}/models/sync`, {});
  }

  listModels(id: string): Observable<ProviderModel[]> {
    return this.http.get<ProviderModel[]>(`/api/providers/${id}/models`);
  }

  addManualModel(id: string, modelId: string): Observable<ProviderModel> {
    return this.http.post<ProviderModel>(`/api/providers/${id}/models`, { modelId });
  }

  selectDefaultModel(id: string, modelId: string): Observable<ProviderConnection> {
    return this.http.put<ProviderConnection>(`/api/providers/${id}/models/default`, { modelId });
  }
}
