# HMI next stop

Run on 17 Nov 2026 with the HMI on a physical phone (Aquaris C, Android 8.1) against the
laboratory API, after a demo reset. The trip line under the vehicle now names the next stop, so
every "Stop passed" shows on screen.

| Screenshot | Trip line |
| --- | --- |
| `01-start.png` | Active trip · next stop 456 (1 of 4) |
| `02-passed-1.png` | Active trip · next stop 459 (2 of 4) |
| `03-all-passed.png` | Active trip · all 4 stops passed |

`HmiUiStateTest` covers these lines plus "Trip ended" and "No trip".
