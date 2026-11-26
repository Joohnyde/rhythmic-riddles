/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.platform.launcher;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 *
 * @author denijal
 */
final class SplashLogPrintStream extends PrintStream {

    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final Deque<String> recentLogs = new ArrayDeque<>();
    private Consumer<String> onLine;

    SplashLogPrintStream(PrintStream console, Consumer<String> onLine) {
        super(console, true, console.charset());
        this.onLine = onLine;
    }

    @Override
    public synchronized void write(int value) {
        super.write(value);
        if (onLine != null) {
            append(value & 0xff);
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        super.write(bytes, offset, length);
        if (onLine != null) {
            for (int i = offset; i < offset + length; i++) {
                append(bytes[i] & 0xff);
            }
        }
    }

    private void append(int value) {
        if (value == '\n') {
            final String text = line.toString(charset())
                    .replaceAll("\\u001B\\[[0-9;]*[A-Za-z]", "") // Replaces ANSI escape characters
                    .strip();
            line.reset();
            recentLogs.addLast(text);
            if (recentLogs.size() > 3) {
                recentLogs.removeFirst();
            }
            onLine.accept(String.join("\n", recentLogs));
        } else {
            line.write(value);
        }
    }

    synchronized void stopCapturing() {
        onLine = null;
        line.reset();
    }

    @Override
    public void close() {
        stopCapturing();
        flush(); // The original console belongs to the application.
    }
}
