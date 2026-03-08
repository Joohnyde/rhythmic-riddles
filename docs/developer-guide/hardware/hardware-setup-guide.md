
# Hardware Setup Guide (Offline 433MHz Buzzers)

This guide is written for someone with **zero electronics experience**. It covers:
- what to buy (BOM)
- how to wire the receiver to the Arduino
- where soldering is needed (and how to avoid doing it yourself)
- how to flash the Arduino firmware
- how to test the system
- how pairing works in the app
- common problems and fixes

## What this hardware does

A team presses a physical button → a 433MHz radio code is sent → a receiver connected to an Arduino detects it → the Arduino sends the code over USB serial to the application → the app assigns that button to a team (pairing) and uses it during gameplay.

### Why 433MHz?
- Works fully offline
- Very cheap parts
- Easy to replace
- No Wi‑Fi setup or credentials



## Bill of Materials

See [bom.md](bom.md) for the complete shopping list, notes, and a cost breakdown table.

## Wiring

![Wiring diagram](https://i.imgur.com/7Xy2V7F.png)

### What you connect

Receiver → Arduino:

- **VCC** → **5V**
- **GND** → **GND**
- **DATA / D0 / OUT** → **D2**

### Why D2?
The firmware listens on interrupt 0, which is mapped to **D2** on Arduino Uno/Nano.

### Beginner tips
- Start by connecting **GND** first (helps avoid mistakes).
- Keep the 3 wires short and tidy.
- If your receiver has multiple data pins, use the main “digital output” pin (often labeled `DATA` or `D0`).


## Antenna soldering (recommended for reliable range)
>  On the previous image, the red circle in the image marks the solder point (ANT pad).  
>  It can be the point next to it or even from the flip side, doesn't matter.
> Only this point requires soldering. All other connections are simple jumper wires.

### The easy way (recommended)
If you do **not** own a soldering iron (most people don’t):
1. Take the receiver board + the wire to:
   - a mobile phone repair shop, or
   - an electronics repair shop.
2. Ask them to solder the wire onto the receiver’s `ANT` pad.

This usually takes 1–2 minutes and is often free or very cheap.

### If you solder yourself
1. Strip ~3mm of wire.
2. “Tin” the wire end with solder.
3. Tin the `ANT` pad (tiny bit of solder on it).
4. Touch pad + wire together and heat briefly.
5. Let it cool without moving the wire.

**Important:** do not keep heat on the pad for long—cheap PCBs can lift pads if overheated.


## Firmware upload (Arduino IDE, step-by-step)

### 1) Install Arduino IDE
Download Arduino IDE here:
https://www.arduino.cc/en/software/

Install it normally for your OS.

### 2) Locate the firmware file in this repo
Firmware source path:
`hardware/firmware/receiver/RhytmicRiddlesReceiver.ino`


### 3) Open the firmware in Arduino IDE
- Arduino IDE → **File → Open…**
- Select `RhytmicRiddlesReceiver.ino`

### 4) Install the required board support (if needed)
Most Nano clones use the classic AVR core.

In Arduino IDE:
1. **Tools → Board → Boards Manager…**
2. Search for: **Arduino AVR Boards**
3. Install it (if not already installed)

### 5) Select the board
- **Tools → Board**
  - Choose **Arduino Nano**

### 6) Select the processor
Some Nano clones require this setting:
- **Tools → Processor → ATmega328P (Old Bootloader)**

If upload fails, try switching between:
- **ATmega328P**
- **ATmega328P (Old Bootloader)**

### 7) Select the serial port
- Plug Arduino into USB
- **Tools → Port**
- Choose the port that appeared after plugging the Arduino in (COMx on Windows, /dev/tty* on Linux/macOS)

### 8) Upload the code
Click the **Upload** button (right arrow).

Expected result:
- “Done uploading.”

### If Arduino IDE can’t see the board (common on Windows)
Many Nano clones use a **CH340 USB serial chip**.
If the COM port doesn’t appear:
- install the CH340 driver (search “CH340 driver” for your OS)
- unplug/replug Arduino
- re-check Tools → Port

## Testing (before pairing)

### 1) Open Serial Monitor
- Arduino IDE → **Tools → Serial Monitor**
- Set baud rate to **9600**

### 2) Press a button
You should see a numeric code printed, e.g.:

```
1234567
```

Notes:
- Each press should produce **one line**
- Holding the button should **not spam** lines (firmware uses a release gap)

If you see nothing, jump to Troubleshooting.


## Pairing in RhythmicRiddles

Pairing is how the application learns which physical button belongs to which team.

### What pairing does
- When you create a team in the Admin UI, the app waits for a button press.
- The next received 433MHz code is stored as that team’s “buzzer code”.
- From then on, that team’s button triggers buzz events.

### Recommended pairing process (safe + repeatable)
1. Start the app and open the Admin UI.
2. Ensure Arduino is connected and producing codes in Serial Monitor (or in app logs).
3. Create teams *one by one*.
4. When prompted, press the correct team’s button once.
5. Select the button that shows up in the UI


### Operational advice for events
- Pair and test **before** the audience arrives.
- Keep spare batteries for remotes.
- Keep one spare Arduino + receiver module if possible (cheap, saves events).


## Serial protocol (for reference)

The Arduino prints **one line per press**:

- Format: `<decimal_code>\n`
- Example: `1234567`
- Baud rate: `9600`

The application reads lines from the serial port and treats each line as one button event.


## Troubleshooting

### I see nothing in Serial Monitor
- Confirm **Tools → Port** is the correct port.
- Confirm baud is **9600**.
- Confirm receiver is wired correctly:
  - VCC → 5V
  - GND → GND
  - DATA → D2
- Try moving the receiver away from the laptop USB area (noise).
- Ensure the receiver has an antenna soldered (range can be near-zero otherwise).

### Upload fails (“programmer not responding”)
- Try **Tools → Processor → ATmega328P (Old Bootloader)** (Nano clones).
- Try a different USB cable (many Type‑C cables are charge-only).
- Try a different USB port.

### Random / garbage codes
- Add antenna.
- Keep wiring short.
- Ensure stable 5V supply (avoid weak USB hubs).

### Range is very poor
- Antenna is the #1 fix (17cm).
- Avoid metal tables/boxes near receiver.
- Consider upgrading receiver module (RXB6 style).

### Buttons work in Arduino but not in the app
- Ensure the Java app is listening to the correct serial port.
- Ensure no other program is holding the port (Serial Monitor might block it).
  - Close Serial Monitor when testing in the app, or use app-only test mode.


## Future alternatives (online / internet buttons)

If you later switch to online buzzers, options in a similar price range include:

- **ESP32 Wi‑Fi button** (each team has a small Wi‑Fi device; sends events over Wi‑Fi)
- **Phone-based buzzers** (teams join a room in a web page; press “BUZZ”)
- **Hybrid**: a “big red button” wired to an ESP32 (feels like TV shows but uses Wi‑Fi)

These reduce RF noise issues and can integrate cleanly with WebSockets, but require:
- a network (Wi‑Fi or hotspot)
- a more complex setup story

For MVP offline deployments, 433MHz remains the simplest and cheapest.
