export class Stage1AbortError extends Error {
  constructor() {
    super('Stage 1 focus preparation was aborted.');
    this.name = 'Stage1AbortError';
  }
}

export function isStage1AbortError(error: unknown): error is Stage1AbortError {
  return error instanceof Stage1AbortError;
}

export function throwIfStage1Aborted(signal?: AbortSignal): void {
  if (signal?.aborted) {
    throw new Stage1AbortError();
  }
}

export function waitForStage1AnimationFrame(signal?: AbortSignal): Promise<void> {
  throwIfStage1Aborted(signal);

  return new Promise<void>((resolve, reject) => {
    let frame: number | undefined;

    const cleanup = (): void => {
      if (frame !== undefined) {
        cancelAnimationFrame(frame);
        frame = undefined;
      }
      signal?.removeEventListener('abort', abort);
    };
    const abort = (): void => {
      cleanup();
      reject(new Stage1AbortError());
    };

    signal?.addEventListener('abort', abort, { once: true });
    frame = requestAnimationFrame(() => {
      cleanup();
      resolve();
    });
  });
}

export function waitForStage1Timeout(ms: number, signal?: AbortSignal): Promise<void> {
  throwIfStage1Aborted(signal);

  return new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      cleanup();
      resolve();
    }, ms);
    const cleanup = (): void => {
      window.clearTimeout(timeout);
      signal?.removeEventListener('abort', abort);
    };
    const abort = (): void => {
      cleanup();
      reject(new Stage1AbortError());
    };

    signal?.addEventListener('abort', abort, { once: true });
  });
}
