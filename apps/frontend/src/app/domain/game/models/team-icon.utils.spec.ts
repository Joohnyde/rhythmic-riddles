import { describe, expect, it } from 'vitest';
import { teamIconColor } from './team-icon.utils';

describe('teamIconColor', () => {
  it('extracts and normalizes colors from supported generated icon names', () => {
    expect(teamIconColor('/team-icons/cat_a94dfb.png')).toBe('#A94DFB');
    expect(teamIconColor('/team-icons/cat_FF6A5F.jpg')).toBe('#FF6A5F');
  });

  it('does not infer a color from malformed or unsupported asset names', () => {
    expect(teamIconColor('/team-icons/cat.png')).toBeUndefined();
    expect(teamIconColor('/team-icons/cat_12345.png')).toBeUndefined();
    expect(teamIconColor('/team-icons/cat_A94DFB.jpeg')).toBeUndefined();
    expect(teamIconColor('/team-icons/cat_A94DFB.webp')).toBeUndefined();
  });
});
