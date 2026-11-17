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
      // Starting a round is a critical state-changing request. Never turn a
      // rejected start into HTTP 200: the Admin would navigate as if it worked.
      const isStartRequest = request.method === 'POST' &&
        /\/api\/v1\/games\/[^/]+\/categories\/[^/?]+\/start(?:\?|$)/.test(request.urlWithParams);
      if (
        !isStartRequest &&
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
