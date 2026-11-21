import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { sessionInterceptor } from './interceptor/session.interceptor';

/**
 * Every call is relative and reaches the parser through the dev server's proxy, so the browser
 * sees one origin. That is what lets HttpClient attach the X-XSRF-TOKEN header on its own: it
 * reads the XSRF-TOKEN cookie the parser sets, for same-origin requests only.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([sessionInterceptor]))
  ]
};
