
## Bill of Materials (BOM)

This BOM lists the parts used for the current offline buzzer MVP (433MHz).  
Links are included for convenience (AliExpress). Prices vary; fill in the “Unit price” column when ordering.

### Core parts (MVP)

| Item | Qty | Link | Notes |  Price (EUR) |
|---|---:|---|---|---:|
| 433MHz Superheterodyne Receiver + Transmitters kit | 1 | https://a.aliexpress.com/_EyXQVUU | Comes with two antennas; solder the **big antenna** to the **big PCB** at the pad marked `ANT`. | 1.77 | 
| Arduino Nano V3.0 (USB Type‑C recommended) | 1 | https://a.aliexpress.com/_EGinpuG | Pick a Nano with Type‑C soldered (easier + sturdier). | 2.44 |
| USB Type‑C data cable | 1 | https://a.aliexpress.com/_EuFTkPW | Must support **data** (not charge‑only). Length not important. | 1.49 |
| 433MHz RF Buttons (transmitters) | 3 | https://a.aliexpress.com/_EJRNUgu | This listing includes a receiver you can ignore; we use it mainly for the buttons. Any 433MHz remotes can work. |  7.09 |
| Jumper wires (female–female) | 3 | https://a.aliexpress.com/_EJrq9ag | I got my from TU Wien; any jumper wires are fine. | 1.07 (for 10 pcs.) |

### Optional upgrades

| Item | Why | Notes |
|---|---|---|
| Better 433MHz receiver module (e.g., RXB6 style) | Improves reliability and range | Drop-in compatible in most cases (still VCC/GND/DATA). |
| Enclosure (plastic project box) | Protects wiring | Avoid metal boxes (range reduction). |
| Screw terminals / Dupont housings | More robust connections | Useful for “event” deployments. |

### Cost breakdown


| Category | Estimated cost (EUR) |
|---|---:|
| Core electronics (Arduino + receiver + buttons) | 12.79 |
| Cables and wires | 1.07 |
| Total | 13.86 |

