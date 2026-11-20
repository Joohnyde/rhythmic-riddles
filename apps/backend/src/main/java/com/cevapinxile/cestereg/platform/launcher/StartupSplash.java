package com.cevapinxile.cestereg.platform.launcher;

import java.awt.AWTError;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class StartupSplash {

  private final JWindow window;
  private final SplashPanel panel;
  private final PrintStream consoleOut = System.out;
  private final PrintStream consoleErr = System.err;
  private PrintStream splashOut;
  private PrintStream splashErr;

  private StartupSplash(BufferedImage background, BufferedImage orbit) {
    window = new JWindow();
    panel = new SplashPanel(background, orbit);
    window.add(panel);
    window.pack();
    window.setLocationRelativeTo(null);
    window.setVisible(true);
    new Timer(50, event -> {
      if (!window.isDisplayable()) {
        ((Timer) event.getSource()).stop();
      } else {
        panel.repaint();
      }
    }).start();
  }

  public static StartupSplash show() {
    if (GraphicsEnvironment.isHeadless()) {
      return null;
    }
    try {
      final BufferedImage background = ImageIO.read(StartupSplash.class.getResource("/splash/starting.png"));
      final BufferedImage orbit = ImageIO.read(StartupSplash.class.getResource("/splash/orbit.png"));
      final StartupSplash[] result = new StartupSplash[1];
      SwingUtilities.invokeAndWait(() -> result[0] = new StartupSplash(background, orbit));
      result[0].captureLogs();
      return result[0];
    } catch (IOException | InterruptedException | InvocationTargetException | AWTError | IllegalArgumentException failure) {
      return null; // Backend startup must work without a usable desktop or splash resources.
    }
  }

  private void captureLogs() {
    splashOut = tee(consoleOut);
    splashErr = tee(consoleErr);
    System.setOut(splashOut);
    System.setErr(splashErr);
  }

  private PrintStream tee(PrintStream console) {
    return new PrintStream(new OutputStream() {
      private final ByteArrayOutputStream line = new ByteArrayOutputStream();

      @Override
      public synchronized void write(int value) {
        console.write(value);
        append(value);
      }

      @Override
      public synchronized void write(byte[] bytes, int offset, int length) {
        console.write(bytes, offset, length);
        for (int i = offset; i < offset + length; i++) {
          append(bytes[i] & 0xff);
        }
      }

      private void append(int value) {
        if (value == '\n') {
          final String message = line.toString(StandardCharsets.UTF_8);
          line.reset();
          SwingUtilities.invokeLater(() -> panel.log(message));
        } else if (value != '\r' && line.size() < 2048) {
          line.write(value);
        }
      }

      @Override
      public void flush() {
        console.flush();
      }
    }, true, StandardCharsets.UTF_8);
  }

  public void close() {
    restoreConsole();
    SwingUtilities.invokeLater(window::dispose);
  }

  public void failed(Throwable failure) {
    restoreConsole();
    SwingUtilities.invokeLater(() -> {
      window.dispose();
      JOptionPane.showMessageDialog(null,
          failure.getMessage() == null ? "See the application logs." : failure.getMessage(),
          "Rhythmic Riddles — Startup Failed", JOptionPane.ERROR_MESSAGE);
    });
  }

  private void restoreConsole() {
    if (System.out == splashOut) {
      System.setOut(consoleOut);
    }
    if (System.err == splashErr) {
      System.setErr(consoleErr);
    }
  }

  private static final class SplashPanel extends JPanel {
    private static final int[] BAR_HEIGHTS = {12, 20, 28, 18, 14};
    private static final Color[] BAR_COLORS = {
        new Color(255, 159, 110), new Color(255, 106, 95), new Color(181, 61, 214),
        new Color(62, 184, 240), new Color(219, 142, 241)
    };

    private final BufferedImage background;
    private final BufferedImage orbit;
    private final long started = System.nanoTime();
    private final Deque<String> logs = new ArrayDeque<>();

    private SplashPanel(BufferedImage background, BufferedImage orbit) {
      this.background = background;
      this.orbit = orbit;
      setPreferredSize(new java.awt.Dimension(background.getWidth(), background.getHeight()));
    }

    private void log(String value) {
      final String line = value.replaceAll("\\u001B\\[[0-9;]*[A-Za-z]", "").strip();
      if (!line.isEmpty()) {
        logs.addLast(line.length() > 110 ? line.substring(0, 107) + "..." : line);
        if (logs.size() > 3) {
          logs.removeFirst();
        }
        repaint();
      }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      final Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(background, 0, 0, null);
      final double ms = (System.nanoTime() - started) / 1_000_000.0;

      g.translate(450, 156);
      g.rotate(ms * 2 * Math.PI / 3000);
      g.drawImage(orbit, -80, -80, null);
      g.rotate(-ms * 2 * Math.PI / 3000);
      g.translate(-450, -156);

      for (int i = 0; i < BAR_HEIGHTS.length; i++) {
        final double phase = (ms / 1200 - i * 0.15) % 2;
        final double progress = phase < 0 ? 0 : phase <= 1 ? phase : 2 - phase;
        final int height = (int) Math.round(BAR_HEIGHTS[i] * (0.35 + 0.65 * progress));
        g.setColor(BAR_COLORS[i]);
        g.fillRoundRect(430 + i * 9, 376 - height / 2, 4, height, 3, 3);
      }
      final double pulse = 0.4 + 0.6 * (0.5 + 0.5 * Math.cos(ms * 2 * Math.PI / 1400));
      g.setColor(new Color(181, 61, 214, (int) (pulse * 85)));
      g.fillOval(375, 315, 12, 12);
      g.setColor(new Color(181, 61, 214, (int) (pulse * 255)));
      g.fillOval(377, 317, 8, 8);

      if (!logs.isEmpty()) {
        g.setColor(new Color(20, 15, 31, 255));
        g.fillRoundRect(18, 499, 710, 48, 10, 10);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.setColor(new Color(184, 170, 204, 255));
        int y = 513;
        for (String line : logs) {
          g.drawString(line, 29, y);
          y += 13;
        }
      }
      g.dispose();
    }
  }
}
