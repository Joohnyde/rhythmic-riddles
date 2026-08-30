package com.cevapinxile.cestereg.io.buzzer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.core.service.BuzzerService;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BuzzerSerialAdapterTest {

  @Mock private BuzzerService buzzerService;
  @Mock private SerialPort port;

  private BuzzerSerialAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BuzzerSerialAdapter();
    ReflectionTestUtils.setField(adapter, "buzzerService", buzzerService);
  }

  @Test
  void connectedReceiverForwardsTrimmedCodes() {
    connect();
    final SerialPortMessageListener listener = capturedListener();
    final SerialPortEvent data = org.mockito.Mockito.mock(SerialPortEvent.class);
    when(data.getEventType()).thenReturn(SerialPort.LISTENING_EVENT_DATA_RECEIVED);
    when(data.getReceivedData()).thenReturn(" 1671\r\n".getBytes(StandardCharsets.US_ASCII));

    listener.serialEvent(data);

    verify(buzzerService).buzz("1671");
  }

  @Test
  void listenerIgnoresEmptyFramesAndIdentificationEchoes() {
    connect();
    final SerialPortMessageListener listener = capturedListener();
    final SerialPortEvent data = org.mockito.Mockito.mock(SerialPortEvent.class);
    when(data.getEventType()).thenReturn(SerialPort.LISTENING_EVENT_DATA_RECEIVED);

    when(data.getReceivedData()).thenReturn(" \r\n".getBytes(StandardCharsets.US_ASCII));
    listener.serialEvent(data);
    when(data.getReceivedData())
        .thenReturn("RHYTMIC_RIDDLES\n".getBytes(StandardCharsets.US_ASCII));
    listener.serialEvent(data);

    verifyNoInteractions(buzzerService);
  }

  @Test
  void disconnectEventClosesTheConnectedPort() {
    connect();
    final SerialPortMessageListener listener = capturedListener();
    final SerialPortEvent disconnect = org.mockito.Mockito.mock(SerialPortEvent.class);
    when(disconnect.getEventType()).thenReturn(SerialPort.LISTENING_EVENT_PORT_DISCONNECTED);

    listener.serialEvent(disconnect);

    verify(port).removeDataListener();
    verify(port).closePort();
    verifyNoInteractions(buzzerService);
  }

  @Test
  void buzzerServiceFailurePropagatesFromTheSerialCallback() {
    connect();
    final SerialPortMessageListener listener = capturedListener();
    final SerialPortEvent data = org.mockito.Mockito.mock(SerialPortEvent.class);
    when(data.getEventType()).thenReturn(SerialPort.LISTENING_EVENT_DATA_RECEIVED);
    when(data.getReceivedData()).thenReturn("1671\n".getBytes(StandardCharsets.US_ASCII));
    org.mockito.Mockito.doThrow(new IllegalStateException("service failure"))
        .when(buzzerService)
        .buzz("1671");

    assertThrows(IllegalStateException.class, () -> listener.serialEvent(data));
  }

  @Test
  void candidateThatCannotOpenIsRejectedWithoutAttachingAListener() {
    when(port.openPort()).thenReturn(false);

    final Boolean connected = ReflectionTestUtils.invokeMethod(adapter, "tryConnect", port);

    assertFalse(Boolean.TRUE.equals(connected));
    verify(port).setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
    verify(port).setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);
    verify(port, never()).addDataListener(any());
    verifyNoInteractions(buzzerService);
  }

  @Test
  void matchingIdentificationAcceptsReceiverAndRegistersListener() {
    when(port.openPort()).thenReturn(true);
    stubSerialRead("RHYTMIC_RIDDLES\n");

    final Boolean connected = ReflectionTestUtils.invokeMethod(adapter, "tryConnect", port);

    assertTrue(Boolean.TRUE.equals(connected));
    final ArgumentCaptor<byte[]> identification = ArgumentCaptor.forClass(byte[].class);
    verify(port).writeBytes(identification.capture(), anyInt());
    assertArrayEquals(
        "RHYTMIC_RIDDLES\n".getBytes(StandardCharsets.US_ASCII), identification.getValue());
    verify(port).addDataListener(any(SerialPortMessageListener.class));
    verify(port, never()).closePort();
    verifyNoInteractions(buzzerService);
  }

  @Test
  void nonMatchingIdentificationRejectsAndClosesCandidate() {
    when(port.openPort()).thenReturn(true);
    stubSerialRead("SOME_OTHER_DEVICE\n");

    final Boolean connected = ReflectionTestUtils.invokeMethod(adapter, "tryConnect", port);

    assertFalse(Boolean.TRUE.equals(connected));
    verify(port).closePort();
    verify(port, never()).addDataListener(any());
    verifyNoInteractions(buzzerService);
  }

  @Test
  void connectRegistersNewlineDelimitedMessageListener() {
    connect();

    final SerialPortMessageListener listener = capturedListener();
    org.junit.jupiter.api.Assertions.assertArrayEquals(
        new byte[] {'\n'}, listener.getMessageDelimiter());
    org.junit.jupiter.api.Assertions.assertTrue(listener.delimiterIndicatesEndOfMessage());
    org.junit.jupiter.api.Assertions.assertEquals(
        SerialPort.LISTENING_EVENT_DATA_RECEIVED | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED,
        listener.getListeningEvents());
  }

  @Test
  void burstFramesPreserveOrderWithoutDuplicateCallbacks() {
    connect();
    final SerialPortMessageListener listener = capturedListener();
    final SerialPortEvent data = org.mockito.Mockito.mock(SerialPortEvent.class);
    when(data.getEventType()).thenReturn(SerialPort.LISTENING_EVENT_DATA_RECEIVED);

    when(data.getReceivedData()).thenReturn("101\n".getBytes(StandardCharsets.US_ASCII));
    listener.serialEvent(data);
    when(data.getReceivedData()).thenReturn("202\r\n".getBytes(StandardCharsets.US_ASCII));
    listener.serialEvent(data);
    when(data.getReceivedData()).thenReturn("303\n".getBytes(StandardCharsets.US_ASCII));
    listener.serialEvent(data);

    final InOrder ordered = inOrder(buzzerService);
    ordered.verify(buzzerService).buzz("101");
    ordered.verify(buzzerService).buzz("202");
    ordered.verify(buzzerService).buzz("303");
    ordered.verifyNoMoreInteractions();
  }

  @Test
  void stopClosesConnectedPortAndLeavesAdapterStopped() {
    connect();
    ReflectionTestUtils.setField(adapter, "running", true);

    adapter.stop();

    verify(port).removeDataListener();
    verify(port).closePort();
    assertFalse(adapter.isRunning());
  }

  private void stubSerialRead(final String response) {
    final byte[] bytes = response.getBytes(StandardCharsets.US_ASCII);
    final AtomicInteger index = new AtomicInteger();
    when(port.readBytes(any(byte[].class), anyInt()))
        .thenAnswer(
            invocation -> {
              final int current = index.getAndIncrement();
              if (current >= bytes.length) {
                return 0;
              }
              final byte[] target = invocation.getArgument(0);
              target[0] = bytes[current];
              return 1;
            });
  }

  private void connect() {
    ReflectionTestUtils.invokeMethod(adapter, "connect", port);
  }

  private SerialPortMessageListener capturedListener() {
    final ArgumentCaptor<SerialPortMessageListener> listener =
        ArgumentCaptor.forClass(SerialPortMessageListener.class);
    verify(port).addDataListener(listener.capture());
    return listener.getValue();
  }
}
