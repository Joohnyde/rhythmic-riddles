# Wiring

Example wiring for a typical 433MHz receiver + Arduino Uno/Nano.

> Receiver pin names vary by module. Always check the module silkscreen.

## Common receiver pins
- VCC (5V)
- GND
- DATA (sometimes two data pins; either works)

## Arduino connections
- Receiver VCC → Arduino 5V
- Receiver GND → Arduino GND
- Receiver DATA → Arduino D2 (interrupt-capable pin)

The firmware enables receive on interrupt 0 (D2 on Uno):

```cpp
rf.enableReceive(0); // D2
```

## Stability tips
- keep data wire short
- avoid running receiver near laptop power bricks / noisy adapters
- consider an external antenna if range is poor

