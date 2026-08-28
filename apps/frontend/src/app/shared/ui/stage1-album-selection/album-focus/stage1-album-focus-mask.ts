import {
  clampStage1Progress,
  formatStage1Percent,
  lerpStage1,
} from './stage1-album-focus-animation';
import { getStage1DiagonalMaskAxes } from './stage1-album-focus-geometry';

/** Builds the directional mask used while neighboring albums collapse toward the selection. */
export function createStage1DirectionalMask(
  angle: string,
  progress: number,
  finalVisibleFraction: number,
  softTailFraction = 0,
): string {
  const collapseProgress = clampStage1Progress(progress);
  const collapseReach = 100 * (1 - finalVisibleFraction);
  const boundary = lerpStage1(0, collapseReach, collapseProgress);
  const tail = 100 * softTailFraction * collapseProgress;
  const diagonalAxes = getStage1DiagonalMaskAxes(angle);

  let ramp: string;
  if (softTailFraction > 0) {
    const tailStart = boundary - tail;
    const tailStop = (amount: number): string =>
      formatStage1Percent(lerpStage1(tailStart, boundary, amount));
    ramp = `transparent 0%, transparent ${formatStage1Percent(tailStart)}, rgb(0 0 0 / 0.06) ${tailStop(0.16)}, rgb(0 0 0 / 0.16) ${tailStop(0.32)}, rgb(0 0 0 / 0.32) ${tailStop(0.5)}, rgb(0 0 0 / 0.55) ${tailStop(0.68)}, rgb(0 0 0 / 0.78) ${tailStop(0.84)}, #000 ${formatStage1Percent(boundary)}, #000 100%`;
  } else {
    const feather = 14;
    const transparentStop = boundary - feather * 0.58;
    const opaqueStop = boundary + feather * 0.42;
    const span = Math.max(1, opaqueStop - transparentStop);
    const stop = (amount: number): string => formatStage1Percent(transparentStop + span * amount);
    ramp = `transparent 0%, transparent ${formatStage1Percent(transparentStop)}, rgb(0 0 0 / 0.08) ${stop(0.15)}, rgb(0 0 0 / 0.24) ${stop(0.34)}, rgb(0 0 0 / 0.48) ${stop(0.56)}, rgb(0 0 0 / 0.74) ${stop(0.78)}, #000 ${formatStage1Percent(opaqueStop)}, #000 100%`;
  }

  if (diagonalAxes) {
    return diagonalAxes.map((axisAngle) => `linear-gradient(${axisAngle}, ${ramp})`).join(', ');
  }

  return `linear-gradient(${angle}, ${ramp})`;
}
