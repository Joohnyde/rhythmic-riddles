package com.cevapinxile.cestereg.core.service.support;

import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Calculates played time using the union of paused and TV-loading intervals. */
public final class PlaybackTimeline {

  private PlaybackTimeline() {}

  /**
   * Computes elapsed playback seconds between {@code start} and {@code now}, excluding all
   * overlapping non-playing intervals exactly once. Open intervals end at {@code now}.
   */
  public static double seekSeconds(final LocalDateTime start, final LocalDateTime now,
      final List<InterruptFrame> blocked) {
    if (!now.isAfter(start)) {
      return 0;
    }
    final List<InterruptFrame> clipped = new ArrayList<>();
    for (InterruptFrame interval : blocked) {
      if (interval.getStart() == null) {
        continue;
      }
      final LocalDateTime from = interval.getStart().isBefore(start) ? start : interval.getStart();
      final LocalDateTime rawEnd = interval.getEnd() == null ? now : interval.getEnd();
      final LocalDateTime to = rawEnd.isAfter(now) ? now : rawEnd;
      if (to.isAfter(from)) {
        clipped.add(new InterruptFrame(from, to));
      }
    }
    clipped.sort(Comparator.comparing(InterruptFrame::getStart));
    LocalDateTime end = start;
    long blockedMillis = 0;
    for (InterruptFrame interval : clipped) {
      final LocalDateTime from = interval.getStart().isAfter(end) ? interval.getStart() : end;
      if (interval.getEnd().isAfter(from)) {
        blockedMillis += Duration.between(from, interval.getEnd()).toMillis();
      }
      if (interval.getEnd().isAfter(end)) {
        end = interval.getEnd();
      }
    }
    return Math.max(0, Duration.between(start, now).toMillis() - blockedMillis) / 1000.0;
  }
}
