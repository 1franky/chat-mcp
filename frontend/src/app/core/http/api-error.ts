import { HttpErrorResponse } from '@angular/common/http';

interface ProblemDetail {
  detail?: string;
}

export function apiErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Ocurrio un error inesperado. Intenta nuevamente.';
  }
  if (error.status === 0) {
    return 'No fue posible conectar con el backend.';
  }
  if (error.status === 401) {
    return 'El correo o la contrasena no son validos.';
  }
  if (error.status === 403) {
    return problemDetail(error) ?? 'No tienes permisos para realizar esta operacion.';
  }
  if (error.status === 429) {
    return 'Demasiados intentos. Espera un momento antes de volver a intentar.';
  }
  return problemDetail(error) ?? 'No fue posible completar la operacion.';
}

function problemDetail(error: HttpErrorResponse): string | null {
  const body = error.error as ProblemDetail | null;
  return typeof body?.detail === 'string' ? body.detail : null;
}
