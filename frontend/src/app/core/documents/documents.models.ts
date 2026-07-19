export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETING';

export interface DocumentView {
  id: string;
  originalFilename: string;
  mimeType: string;
  byteSize: number;
  status: DocumentStatus;
  failureReason: string | null;
  chunkCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentPage {
  items: DocumentView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
