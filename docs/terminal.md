# AccessibleBusApp – Light and Proximity Sensors

## 1. Objective

This activity identifies and tests the light and proximity sensors available on an Android device. Sensor measurements and Activity lifecycle events are inspected through Android Logcat.

The application uses the sensors to observe ambient illumination and nearby objects. Sensor listeners are registered while the Activity is in the foreground and unregistered when it is paused.

## 2. Test Environment

| Property | Value |
|---|---|
| Device | Motorola moto g35 5G |
| Operating system | Android 14 |
| Android API level | 34 |
| Application package | `dte.masteriot.accessiblebus` |
| Implementation language | Kotlin |
| Main Activity | `MainActivity` |
| Logcat tag | `C02_SENSORES` |

## 3. Sensor Identification

The sensors were obtained through Android's `SensorManager`.

### 3.1 Light Sensor

| Property | Value |
|---|---|
| Sensor name | `light_ltr569` |
| Sensor type | `TYPE_LIGHT` |
| Android sensor type | 5 |
| Vendor | liteon |
| Maximum range | 65535 lux |
| Resolution | 1 lux |
| Unit | lux |

The sensor reports ambient illumination. Its readings decrease when it is covered and increase when exposed to more light.

### 3.2 Proximity Sensor

| Property | Value |
|---|---|
| Sensor name | `prox_ltr569` |
| Sensor type | `TYPE_PROXIMITY` |
| Android sensor type | 8 |
| Vendor | liteon |
| Maximum range | 5.0 |
| Resolution | 1.0 |

On the tested device, proximity readings behaved as a two-state measurement:

- `0.0`: an object is detected nearby.
- `5.0`: no object is detected within the sensor's range.

The sensor's reported maximum range is 5.0. The observed two-state behavior is specific to this device and should not be assumed for all Android devices.

## 4. Implementation and Lifecycle

The Activity implements `SensorEventListener`. The main callbacks are:

- `onSensorChanged()`: receives sensor measurements.
- `onAccuracyChanged()`: receives sensor accuracy changes.
- `onResume()`: registers sensor listeners.
- `onPause()`: unregisters sensor listeners.

The application requests `SensorManager.SENSOR_DELAY_NORMAL`. Both `registerListener()` calls returned `true` during testing.

The first value in the light sensor event is illumination in lux. The first value in the proximity event is the reported proximity value. Measurements are logged with the `C02_SENSORES` tag.

Listeners are registered in `onResume()` and unregistered in `onPause()`. Therefore, acquisition is not continuous while the Activity is stopped or the screen is off. The listeners register again when the Activity returns to the foreground.

## 5. Experimental Measurements

The following table summarizes the approximate light readings observed in the tests. Values are approximate and depend on device placement and the light reaching the sensor.

| Situation | Observed light level |
|---|---:|
| Dark room | Approximately 0–5 lux |
| Sensor uncovered, device in a pocketing/transition sequence before insertion | Approximately 14–32 lux |
| Sensor partially covered | Around 5 lux |
| Sensor covered by a hand | Approximately 0–5 lux |
| Device inside a pocket | Approximately 0–2 lux |

The pocket test also produced these specific readings:

```text
Light: 9.375 lux
Proximity: 0.0
Light: 3.25 lux
Light: 1.375 lux
```

After removing the device from the pocket:

```text
Proximity: 5.0
Light: 22.125 lux
Light: 37.625 lux
```

For uncovered conditions, other observed readings included approximately 35 lux on a table and around 50 lux in a well-lit area. The sensor does not necessarily report exactly 0 lux when covered.

### 5.1 Proximity Measurements

| Situation | Observed value |
|---|---:|
| No object nearby | `5.0` |
| Hand close to the sensor | `0.0` |
| Device inside a pocket | `0.0` |
| Device removed from the pocket | `5.0` |

### 5.2 Pocket Test

The device was placed inside a pocket while the application remained in the foreground. Light readings decreased and proximity changed from `5.0` to `0.0`. When the device was removed, light readings increased and proximity returned to `5.0`.

No `onPause()` or `onStop()` callback was observed in the corresponding sequence. Sensor events continued while the Activity remained in the foreground.

## 6. Activity and Screen Tests

### Switching Applications

When the application was moved to the background, `onPause()` and `onStop()` were observed and the listeners were unregistered. On returning to the application, `onStart()` and `onResume()` were observed. Both listener registrations returned `true`, and measurements resumed.

### Screen Off and Unlock

Turning the screen off caused the Activity to pause and stop, and the listeners were unregistered. After unlocking, the Activity resumed, both listeners registered successfully, and sensor measurements resumed.

### Screen Rotation

Logcat showed Activity pause/stop lifecycle events during rotation, followed by Activity start/resume events. Both sensor listeners registered again and new measurements were received. The rotation test passed.

## 7. Initial Light Thresholds

Based on the observed dark/covered readings (approximately 0–5 lux) and uncovered readings (approximately 22–50 lux), the following initial thresholds are proposed:

| Parameter | Initial value | Meaning |
|---|---:|---|
| `L_osc` | 5 lux | Upper boundary for the dark state |
| `L_luz` | 20 lux | Lower boundary for the sufficiently lit state |

Initial classification:

- `lux <= 5`: dark.
- `5 < lux < 20`: intermediate illumination.
- `lux >= 20`: sufficiently lit.

These are initial thresholds derived from the observed measurements; further testing may be required to validate them.

## 8. Sensor Availability and Degraded Operation

The application should check sensor availability before relying on a sensor.

- If the light sensor is unavailable, light-dependent behavior should be disabled or use a documented default. No artificial light readings should be generated.
- If the proximity sensor is unavailable, proximity-dependent behavior should be disabled. The application must not assume that an object is near or far.
- The application should continue operating without crashing when either sensor is unavailable.

## 9. Text-to-Speech

Text-to-Speech was tested using Google Speech Recognition and Synthesis with an English (United States) voice. The test was successful. English is the language used by the application.

## 10. Results and Conclusion

| Test | Result |
|---|---|
| Sensor identification | Successful |
| Light measurements | Successful |
| Proximity measurements | Successful |
| Light response to covering and exposure | Successful |
| Proximity response to a nearby object | Successful |
| Pocket behavior | Successful |
| Listener unregistration on pause | Successful |
| Listener registration on resume | Successful |
| Recovery after switching applications | Successful |
| Recovery after screen off and unlock | Successful |
| Recovery after screen rotation | Successful |
| English TTS test | Successful |

The light and proximity sensors were identified and tested successfully on the Motorola moto g35 5G running Android 14 (API 34). Sensor readings responded to changes in illumination and proximity. The application correctly manages sensor listeners through the Activity lifecycle and resumes acquisition after returning to the foreground, unlocking the device, or rotating the screen.

Continuous background sensor acquisition is not implemented in this version.
