import { HttpErrorResponse } from '@angular/common/http';
import { apiErrorMessage } from './api-error';

describe('apiErrorMessage', () => {
  it.each([
    [401, 'correo o la contrasena'],
    [403, 'permisos'],
    [429, 'Demasiados intentos'],
  ])('maps HTTP %s to a safe message', (status, expected) => {
    const error = new HttpErrorResponse({ status });
    expect(apiErrorMessage(error)).toContain(expected);
  });
});
