import type { AlbumFocusOrigin } from './stage1-album-focus.types';

export interface Stage1FocusTransform {
  readonly x: number;
  readonly y: number;
  readonly scale: number;
}

const FOCUS_EASING_X1 = 0.45;
const FOCUS_EASING_Y1 = 0;
const FOCUS_EASING_X2 = 0.2;
const FOCUS_EASING_Y2 = 1;

export function clampStage1Progress(value: number, min = 0, max = 1): number {
  return Math.min(max, Math.max(min, value));
}

export function lerpStage1(start: number, end: number, progress: number): number {
  return start + (end - start) * progress;
}

export function smoothstepStage1(start: number, end: number, value: number): number {
  if (value <= start) return 0;
  if (value >= end) return 1;
  const progress = (value - start) / (end - start);
  return progress * progress * (3 - 2 * progress);
}

export function stage1FocusEasedProgress(progress: number): number {
  const targetX = clampStage1Progress(progress);
  let t = targetX;
  for (let index = 0; index < 5; index += 1) {
    const currentX = sampleBezier(FOCUS_EASING_X1, FOCUS_EASING_X2, t) - targetX;
    const derivative = sampleBezierDerivative(FOCUS_EASING_X1, FOCUS_EASING_X2, t);
    if (Math.abs(currentX) < 0.00001 || derivative === 0) break;
    t = clampStage1Progress(t - currentX / derivative);
  }
  return sampleBezier(FOCUS_EASING_Y1, FOCUS_EASING_Y2, t);
}

export function interpolateStage1FocusTransform(
  start: Stage1FocusTransform,
  target: Stage1FocusTransform,
  rawProgress: number,
): Stage1FocusTransform {
  const progress = stage1FocusEasedProgress(rawProgress);
  return {
    x: lerpStage1(start.x, target.x, progress),
    y: lerpStage1(start.y, target.y, progress),
    scale: lerpStage1(start.scale, target.scale, progress),
  };
}

export function formatStage1FocusTransform(transform: Stage1FocusTransform): string {
  return `translate3d(${transform.x}px, ${transform.y}px, 0) scale(${transform.scale})`;
}

export function formatStage1Percent(value: number): string {
  return `${value.toFixed(2)}%`;
}

export function transformStage1FocusRect(
  rect: AlbumFocusOrigin,
  transform: Stage1FocusTransform,
): AlbumFocusOrigin {
  return {
    left: rect.left * transform.scale + transform.x,
    top: rect.top * transform.scale + transform.y,
    width: rect.width * transform.scale,
    height: rect.height * transform.scale,
  };
}

function sampleBezier(a1: number, a2: number, t: number): number {
  return 3 * a1 * (1 - t) * (1 - t) * t + 3 * a2 * (1 - t) * t * t + t * t * t;
}

function sampleBezierDerivative(a1: number, a2: number, t: number): number {
  return 3 * a1 * (1 - t) * (1 - t) + 6 * (a2 - a1) * (1 - t) * t + 3 * (1 - a2) * t * t;
}
