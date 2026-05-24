import {
  HttpErrorResponse,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

const TEMPORARILY_IGNORED_STATUS_CODES = new Set([0, 502, 503, 504]);

export const ignoredHttpErrorInterceptor: HttpInterceptorFn = (
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
) => {
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        TEMPORARILY_IGNORED_STATUS_CODES.has(error.status)
      ) {
        return of(
          new HttpResponse({
            body: null,
            status: 200,
            statusText: 'Ignored temporary backend error',
            url: request.urlWithParams,
          }),
        );
      }

      return throwError(() => error);
    }),
  );
};
