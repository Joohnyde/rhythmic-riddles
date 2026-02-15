/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

import java.time.LocalDateTime;

/**
 * @author denijal
 * Represents a pause interval in the song timeline.
 *
 * <p>An {@code InterruptFrame} defines a time window during which playback
 * was paused due to either a team buzz-in or a system interrupt.</p>
 *
 * <p>{@code end} may be {@code null} if the interrupt is still active.</p>
 *
 * <p>Instances of this class are primarily used for seek calculation,
 * where only outermost interrupt frames are considered.</p>
 */
public class InterruptFrame {
    
    private LocalDateTime start, end;

    public InterruptFrame(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }
    
    
    
}
