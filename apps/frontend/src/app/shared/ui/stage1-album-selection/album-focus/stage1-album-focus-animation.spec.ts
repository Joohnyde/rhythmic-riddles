import { describe, expect, it } from 'vitest';
import {
  clampStage1Progress,
  formatStage1FocusTransform,
  interpolateStage1FocusTransform,
  smoothstepStage1,
  stage1FocusEasedProgress,
  transformStage1FocusRect,
} from './stage1-album-focus-animation';

describe('Stage 1 focus animation math', () => {
  it('clamps progress and keeps easing endpoints exact', () => {
    expect(clampStage1Progress(-1)).toBe(0);
    expect(clampStage1Progress(2)).toBe(1);
    expect(stage1FocusEasedProgress(0)).toBeCloseTo(0);
    expect(stage1FocusEasedProgress(1)).toBeCloseTo(1);
  });

  it('interpolates camera transforms monotonically between the exact endpoints', () => {
    const start = { x: -20, y: 10, scale: 1 };
    const target = { x: 120, y: -80, scale: 2.5 };
    expect(interpolateStage1FocusTransform(start, target, 0)).toEqual(start);
    expect(interpolateStage1FocusTransform(start, target, 1)).toEqual(target);
    const middle = interpolateStage1FocusTransform(start, target, 0.5);
    expect(middle.x).toBeGreaterThan(start.x);
    expect(middle.x).toBeLessThan(target.x);
    expect(middle.scale).toBeGreaterThan(start.scale);
    expect(middle.scale).toBeLessThan(target.scale);
  });

  it('keeps smoothstep bounded at fade timing boundaries', () => {
    expect(smoothstepStage1(0.2, 0.8, 0.1)).toBe(0);
    expect(smoothstepStage1(0.2, 0.8, 0.5)).toBeCloseTo(0.5);
    expect(smoothstepStage1(0.2, 0.8, 0.9)).toBe(1);
  });

  it('transforms source rectangles and formats the matching camera CSS transform deterministically', () => {
    const transform = { x: 10, y: -5, scale: 2 };
    expect(
      transformStage1FocusRect({ left: 20, top: 30, width: 40, height: 50 }, transform),
    ).toEqual({
      left: 50,
      top: 55,
      width: 80,
      height: 100,
    });
    expect(formatStage1FocusTransform(transform)).toBe('translate3d(10px, -5px, 0) scale(2)');
  });
});
