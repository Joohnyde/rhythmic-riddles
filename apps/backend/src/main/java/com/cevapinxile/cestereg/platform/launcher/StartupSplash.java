package com.cevapinxile.cestereg.platform.launcher;

import java.awt.AWTError;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

public final class StartupSplash {

  private final JWindow window;
  private final JTextArea logs = new JTextArea();  
  private final PrintStream consoleOut = System.out;
  private final PrintStream consoleErr = System.err;
  private final SplashLogPrintStream splashOut = new SplashLogPrintStream(consoleOut, this::showLog);
  private final SplashLogPrintStream splashErr = new SplashLogPrintStream(consoleErr, this::showLog);
  private volatile boolean closed;

  private StartupSplash() {
    final JLabel background = new JLabel(icon("starting.png"));
    final JLabel animation = new JLabel(icon("animation.gif"));
    animation.setBounds(364, 77, 166, 317);
    logs.setBounds(26, 499, 848, 55);
    logs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
    logs.setForeground(new Color(190, 177, 208));
    logs.setEditable(false);
    logs.setOpaque(false);
    background.setLayout(null);
    background.add(animation);
    background.add(logs);

    window = new JWindow();
    window.setContentPane(background);
    window.pack();
    window.setLocationRelativeTo(null);
    window.setVisible(true);
  }

  private static ImageIcon icon(String name) {
    return new ImageIcon(Objects.requireNonNull(StartupSplash.class.getResource("/splash/" + name)));
  }

  public static StartupSplash show() {
    if (GraphicsEnvironment.isHeadless()) {
      return null;
    }
    try {
      final StartupSplash splash = SwingUtilities.isEventDispatchThread()
          ? new StartupSplash()
          : CompletableFuture.supplyAsync(StartupSplash::new, SwingUtilities::invokeLater).join();
      System.setOut(splash.splashOut);
      System.setErr(splash.splashErr);
      return splash;
    } catch (RuntimeException | AWTError failure) {
      return null; // A splash must not prevent backend startup on a broken desktop.
    }
  }


  private void showLog(String text) {
    SwingUtilities.invokeLater(() -> {
      if (!closed && text != null && !text.isEmpty()) {
        logs.setText(text);
      }
    });
  }
  
  public void close() {
    closed = true;
    splashOut.stopCapturing();
    splashErr.stopCapturing();
    if (System.out == splashOut) {
      System.setOut(consoleOut);
    }
    if (System.err == splashErr) {
      System.setErr(consoleErr);
    }
    SwingUtilities.invokeLater(window::dispose);
  }

  public void failed(Throwable failure) {
    close();
    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
        failure.getMessage() == null ? "See the application logs." : failure.getMessage(),
        "Rhythmic Riddles — Startup Failed", JOptionPane.ERROR_MESSAGE));
  }
}
