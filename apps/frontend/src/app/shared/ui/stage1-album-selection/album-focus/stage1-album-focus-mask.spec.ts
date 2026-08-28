import { describe, expect, it } from 'vitest';
import { createStage1DirectionalMask } from './stage1-album-focus-mask';

describe('Stage 1 directional focus mask', () => {
  it('keeps the retained edge opaque at zero collapse progress', () => {
    expect(createStage1DirectionalMask('to right', 0, 0.15)).toContain('#000 100%');
  });

  it('builds a long feathered tail for immediate neighbors near the final state', () => {
    const mask = createStage1DirectionalMask('to left', 1, 0.15, 0.3);

    expect(mask).toContain('rgb(0 0 0 / 0.06)');
    expect(mask).toContain('rgb(0 0 0 / 0.78)');
    expect(mask).toContain('85.00%');
  });

  it('uses intersectable axis masks for diagonal neighbors', () => {
    const mask = createStage1DirectionalMask('135deg', 0.8, 0.15, 0.3);

    expect(mask.split('linear-gradient(')).toHaveLength(3);
  });
});
