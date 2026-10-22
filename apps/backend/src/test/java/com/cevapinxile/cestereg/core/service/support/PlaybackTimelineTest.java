package com.cevapinxile.cestereg.core.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;
import org.junit.jupiter.api.Test;

class PlaybackTimelineTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 12, 0);

  @Test
  void subtractsUnionOfOverlappingLoadingAndInterruptIntervals() {
    assertEquals(1.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(4), List.of(
        new InterruptFrame(START, START.plusSeconds(3)),
        new InterruptFrame(START.plusSeconds(1), START.plusSeconds(2)))));
  }

  @Test
  void loadingAcrossReconnectInterruptCountsOnlyUninterruptedSegments() {
    // TV registers at T+0, disconnects at T+0.9, reconnects at T+5 and ACKs at T+6.
    // The raw loading window is six seconds; 4.1 seconds overlap the system pause.
    // After the ACK, only real playback (T+6..T+8) should contribute to seek.
    assertEquals(2.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(8), List.of(
        new InterruptFrame(START, START.plusSeconds(6)),
        new InterruptFrame(START.plusNanos(900_000_000), START.plusSeconds(5)))));
  }

  @Test
  void loadingAcrossReconnectRemainsZeroWhileAcknowledgementIsOpen() {
    assertEquals(0.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(6), List.of(
        new InterruptFrame(START, null),
        new InterruptFrame(START.plusNanos(900_000_000), START.plusSeconds(5)))));
  }

  @Test
  void openDelayBlocksPlaybackUntilAcknowledgement() {
    assertEquals(0.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(10), List.of(
        new InterruptFrame(START, null))));
  }

  @Test
  void clipsIntervalsToCurrentAttemptAndDoesNotCountGapTwice() {
    assertEquals(5.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(10), List.of(
        new InterruptFrame(START.minusSeconds(2), START.plusSeconds(2)),
        new InterruptFrame(START.plusSeconds(1), START.plusSeconds(4)),
        new InterruptFrame(START.plusSeconds(8), START.plusSeconds(9)))));
  }

  @Test
  void startDelayThenPlayedAudioThenNestedBufferingAndOpenDelay() {
    // [0,3] loading, [3,9] played, [9,12] buffering, [12,16] played,
    // [16,20] still buffering. System interruptions nested within loading
    // or buffering must not double-subtract the same seconds.
    assertEquals(10.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(20), List.of(
        new InterruptFrame(START, START.plusSeconds(3)),
        new InterruptFrame(START.plusSeconds(1), START.plusSeconds(2)),
        new InterruptFrame(START.plusSeconds(9), START.plusSeconds(12)),
        new InterruptFrame(START.plusSeconds(10), START.plusSeconds(11)),
        new InterruptFrame(START.plusSeconds(16), null),
        new InterruptFrame(START.plusSeconds(17), null))));
  }

  @Test
  void multiplePlayedSegmentsAndOverlappingDelaysAfterResume() {
    // A partially overlapping pair is intentionally merged, unlike naive subtraction.
    assertEquals(8.5, PlaybackTimeline.seekSeconds(START, START.plusSeconds(15), List.of(
        new InterruptFrame(START, START.plusSeconds(2)),
        new InterruptFrame(START.plusSeconds(5), START.plusSeconds(8)),
        new InterruptFrame(START.plusSeconds(7), START.plusSeconds(9)),
        new InterruptFrame(START.plusSeconds(12), START.plusSeconds(12).plusNanos(500_000_000)))));
  }

  @Test
  void clipsOpenIntervalBeginningBeforePlaybackStart() {
    assertEquals(0.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(4), List.of(
        new InterruptFrame(START.minusSeconds(1), null))));
  }

  @Test
  void handlesNoDelaysAndFutureTimestamps() {
    assertEquals(8.0, PlaybackTimeline.seekSeconds(START, START.plusSeconds(8), List.of()));
    assertEquals(0.0, PlaybackTimeline.seekSeconds(START, START.minusSeconds(1), List.of()));
  }
}
