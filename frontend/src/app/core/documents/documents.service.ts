import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DocumentPage, DocumentStatus, DocumentView } from './documents.models';

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly http = inject(HttpClient);

  list(status?: DocumentStatus, page = 0, size = 50): Observable<DocumentPage> {
    const params: Record<string, string | number> = { page, size };
    if (status) {
      params['status'] = status;
    }
    return this.http.get<DocumentPage>('/api/documents', { params });
  }

  get(id: string): Observable<DocumentView> {
    return this.http.get<DocumentView>(`/api/documents/${id}`);
  }

  upload(file: File): Observable<DocumentView> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<DocumentView>('/api/documents', formData);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/documents/${id}`);
  }
}
