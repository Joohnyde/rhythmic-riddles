package com.cevapinxile.cestereg.io.buzzer;

// import com.cevapinxile.cestereg.core.service.BuzzerService;
import com.cevapinxile.cestereg.core.service.BuzzerService;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Automatically discovers and listens to the Rhythmic Riddles USB buzzer receiver.
 *
 * <p>The receiver is identified by sending {@code RHYTMIC_RIDDLES} over each available serial port.
 * The buzzer firmware responds with the same value.
 *
 * <p>Once connected, every subsequent line received from the device is treated as a buzzer code.
 *
 * <p>If the receiver is disconnected, discovery automatically resumes.
 *
 * @author denijal
 */
@Component
public class BuzzerSerialAdapter implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(BuzzerSerialAdapter.class);

  private static final int BAUD_RATE = 9600;
  private static final int DATA_BITS = 8;

  private static final String DEVICE_IDENTIFIER = "RHYTMIC_RIDDLES";

  // How often to look for the receiver when it is not connected.
  private static final long DISCOVERY_INTERVAL_SECONDS = 2;

  // Opening the serial connection resets many Arduino Nano boards.
  // Give the firmware a short moment to boot before asking it to identify itself.
  private static final long DEVICE_BOOT_DELAY_MS = 1500;

  @Autowired private BuzzerService buzzerService;

  private final ScheduledExecutorService discoveryExecutor =
      Executors.newSingleThreadScheduledExecutor();

  private volatile SerialPort serialPort;
  private volatile boolean running;

  /**
   * Starts background discovery of the USB buzzer receiver.
   *
   * <p>The application does not require the receiver to already be connected. It may be plugged in
   * at any time after the backend has started.
   */
  @Override
  public void start() {
    running = true;

    discoveryExecutor.scheduleWithFixedDelay(
        this::discoverReceiver, 0, DISCOVERY_INTERVAL_SECONDS, TimeUnit.SECONDS);

    LOG.info("Buzzer receiver discovery started");
  }

  /** Looks through the currently available serial ports for the Rhythmic Riddles receiver. */
  private void discoverReceiver() {
    if (!running || serialPort != null) {
      return;
    }

    for (final SerialPort candidate : SerialPort.getCommPorts()) {
      if (!running || serialPort != null) {
        return;
      }

      if (tryConnect(candidate)) {
        return;
      }
    }
  }

  /**
   * Opens a candidate serial port and asks the connected device to identify itself.
   *
   * @param candidate serial port to test
   * @return {@code true} when the Rhythmic Riddles receiver was found
   */
  private boolean tryConnect(final SerialPort candidate) {
    candidate.setComPortParameters(
        BAUD_RATE, DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);

    // Prevent identification reads from blocking forever.
    candidate.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);

    if (!candidate.openPort()) {
      return false;
    }

    try {
      LOG.debug("Checking serial port {}", candidate.getSystemPortName());

      // Opening a Nano's USB serial connection commonly resets the board.
      Thread.sleep(DEVICE_BOOT_DELAY_MS);

      // Ask the device whether it is our buzzer receiver.
      write(candidate, DEVICE_IDENTIFIER);

      final long deadline = System.currentTimeMillis() + 1000;

      while (System.currentTimeMillis() < deadline) {
        final String response = readLine(candidate);

        if (DEVICE_IDENTIFIER.equals(response)) {
          connect(candidate);
          return true;
        }
      }
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (final Exception exception) {
      LOG.debug("Could not identify device on {}", candidate.getSystemPortName(), exception);
    }

    candidate.closePort();
    return false;
  }

  /**
   * Marks the discovered receiver as connected and starts listening for buzzer codes
   * asynchronously.
   *
   * @param port identified buzzer receiver serial port
   */
  private void connect(final SerialPort port) {
    serialPort = port;

    port.addDataListener(
        new SerialPortMessageListener() {

          @Override
          public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_RECEIVED
                | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
          }

          @Override
          public byte[] getMessageDelimiter() {
            return new byte[] {'\n'};
          }

          @Override
          public boolean delimiterIndicatesEndOfMessage() {
            return true;
          }

          @Override
          public void serialEvent(final SerialPortEvent event) {

            // Physical USB disconnect: close the old port and allow discovery
            // to find the receiver again when it is plugged back in.
            if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
              disconnect();
              return;
            }

            final String message =
                new String(event.getReceivedData(), StandardCharsets.US_ASCII).trim();

            if (message.isEmpty()) {
              return;
            }

            // Ignore identification responses. Everything else is currently
            // expected to be an RF button code.
            if (DEVICE_IDENTIFIER.equals(message)) {
              return;
            }

            LOG.debug("Buzzer pressed: {}", message);
            buzzerService.buzz(message);
          }
        });

    LOG.info("Buzzer receiver connected on {}", port.getSystemPortName());
  }

  /**
   * Writes a single newline-terminated command to the receiver.
   *
   * @param port serial port
   * @param message command to send
   */
  private void write(final SerialPort port, final String message) {
    final byte[] data = (message + '\n').getBytes(StandardCharsets.US_ASCII);

    port.writeBytes(data, data.length);
  }

  /**
   * Reads one newline-terminated response from a candidate serial device.
   *
   * @param port serial port
   * @return received line, or an empty string when no complete response arrived
   */
  private String readLine(final SerialPort port) {
    final StringBuilder result = new StringBuilder();

    while (true) {
      final byte[] buffer = new byte[1];
      final int read = port.readBytes(buffer, 1);

      if (read <= 0) {
        return "";
      }

      final char character = (char) buffer[0];

      if (character == '\n') {
        return result.toString().trim();
      }

      if (character != '\r') {
        result.append(character);
      }
    }
  }

  /**
   * Closes the current receiver connection.
   *
   * <p>The discovery task remains active and will automatically reconnect if the receiver appears
   * again.
   */
  private synchronized void disconnect() {
    final SerialPort port = serialPort;

    if (port == null) {
      return;
    }

    // Clear first so the discovery loop is allowed to search again.
    serialPort = null;

    port.removeDataListener();
    port.closePort();

    LOG.info("Buzzer receiver disconnected");
  }

  /** Stops USB discovery and closes an existing receiver connection. */
  @Override
  public void stop() {
    running = false;

    disconnect();

    discoveryExecutor.shutdownNow();

    LOG.info("Buzzer receiver listener stopped");
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
