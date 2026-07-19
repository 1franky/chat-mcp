import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { interval } from 'rxjs';
import { apiErrorMessage } from '../../core/http/api-error';
import { DocumentStatus, DocumentView } from '../../core/documents/documents.models';
import { DocumentsService } from '../../core/documents/documents.service';

const NON_TERMINAL_STATUSES: ReadonlySet<DocumentStatus> = new Set([
  'UPLOADED',
  'PROCESSING',
  'DELETING',
]);

@Component({
  selector: 'app-documents-page',
  imports: [MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './documents-page.html',
  styleUrl: './documents-page.scss',
})
export class DocumentsPage {
  private readonly documentsApi = inject(DocumentsService);

  protected readonly documents = signal<DocumentView[]>([]);
  protected readonly loading = signal(true);
  protected readonly uploading = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly deleteConfirmation = signal<string | null>(null);

  private readonly hasPendingDocuments = computed(() =>
    this.documents().some((document) => NON_TERMINAL_STATUSES.has(document.status)),
  );

  constructor() {
    this.loadDocuments();
    interval(3000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (this.hasPendingDocuments()) {
          this.loadDocuments();
        }
      });
  }

  protected triggerUpload(input: HTMLInputElement): void {
    input.click();
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.clearMessages();
    this.documentsApi.upload(file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.notice.set('Documento subido. Procesando…');
        this.loadDocuments();
      },
      error: (error) => {
        this.uploading.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  protected remove(document: DocumentView): void {
    if (this.deleteConfirmation() !== document.id) {
      this.deleteConfirmation.set(document.id);
      return;
    }
    this.clearMessages();
    this.documentsApi.delete(document.id).subscribe({
      next: () => {
        this.deleteConfirmation.set(null);
        this.notice.set('Documento eliminado.');
        this.loadDocuments();
      },
      error: (error) => {
        this.deleteConfirmation.set(null);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  protected formatSize(byteSize: number): string {
    if (byteSize < 1024) {
      return `${byteSize} B`;
    }
    const kb = byteSize / 1024;
    if (kb < 1024) {
      return `${kb.toFixed(1)} KB`;
    }
    return `${(kb / 1024).toFixed(1)} MB`;
  }

  private loadDocuments(): void {
    this.loading.set(true);
    this.documentsApi.list().subscribe({
      next: (page) => {
        this.documents.set(page.items);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  private clearMessages(): void {
    this.error.set('');
    this.notice.set('');
  }
}
