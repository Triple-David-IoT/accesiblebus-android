# AccessibleBusApp – Light and Proximity Sensors

## 1. Overview

This project is part of the AccessibleBusApp application developed for Android.

The objective of this activity is to identify, access, and test the light and proximity sensors available on an Android device. The application retrieves sensor measurements and displays them through the Android logging system.

The tests focus on understanding how the sensors behave under different environmental conditions and how the application handles sensor registration when the Activity enters or leaves the foreground.

The two sensors used in this activity are:

- **Light sensor:** measures the ambient light level in lux.
- **Proximity sensor:** detects whether an object is close to the device.

The application also implements the appropriate Activity lifecycle callbacks to register and unregister the sensor listeners.

---

## 2. Test Environment

The application was tested using the following device and software configuration.

| Property | Value |
|---|---|
| Device | Motorola moto g35 5G |
| Operating system | Android 14 |
| Android API level | 34 |
| Application package | `dte.masteriot.accessiblebus` |
| Implementation language | Kotlin |
| Main Activity | `MainActivity` |
| Logging tag | `C02_SENSORES` |

The sensor measurements and lifecycle events were inspected using Android Logcat.

---

## 3. Sensor Identification

The application uses Android's `SensorManager` to retrieve the sensors available on the device.

The following sensors were identified during testing.

### 3.1. Light Sensor

| Property | Value |
|---|---|
| Sensor name | `light_ltr569` |
| Sensor type | `TYPE_LIGHT` |
| Android sensor type | 5 |
| Vendor | liteon |
| Maximum range | 65535 lux |
| Resolution | 1 lux |
| Unit | lux |

The light sensor measures the ambient illumination surrounding the device.

The measured value changes according to the amount of light reaching the sensor. When the sensor is covered, the measured illumination decreases. When the sensor is exposed to a brighter environment, the measured value increases.

### 3.2. Proximity Sensor

| Property | Value |
|---|---|
| Sensor name | `prox_ltr569` |
| Sensor type | `TYPE_PROXIMITY` |
| Android sensor type | 8 |
| Vendor | liteon |
| Maximum range | 5.0 |
| Resolution | 1.0 |

The proximity sensor detects whether an object is close to the device.

On the tested Motorola device, the sensor reports the following values:

- `0.0`: an object is detected nearby.
- `5.0`: no object is detected within the sensor's detection range.

These values correspond to the behavior observed during the experiments. Other Android devices may report different proximity values.

---

## 4. Implementation

The application accesses the sensors through Android's `SensorManager`.

The light and proximity sensors are obtained using their respective sensor types:

- `Sensor.TYPE_LIGHT`
- `Sensor.TYPE_PROXIMITY`

The Activity implements the `SensorEventListener` interface to receive sensor events.

The main callbacks used are:

- `onSensorChanged()`: receives new sensor measurements.
- `onAccuracyChanged()`: reports changes in sensor accuracy.
- `onResume()`: registers the sensor listeners.
- `onPause()`: unregisters the sensor listeners.

### 4.1. Sensor Registration

The sensor listeners are registered when the Activity enters the foreground.

The application uses `SensorManager.SENSOR_DELAY_NORMAL` as the requested sampling rate.

The registration process is performed in `onResume()`.

The return value of `registerListener()` is also logged to verify whether the registration was successful.

During testing, both sensor registrations returned `true`.

### 4.2. Sensor Unregistration

The listeners are unregistered in `onPause()`.

This prevents the Activity from continuing to receive sensor events when it is no longer in the foreground.

The following method is used:

```kotlin
sensorManager.unregisterListener(this)
```

When the Activity returns to the foreground, the listeners are registered again through `onResume()`.

### 4.3. Sensor Measurements

The application processes the sensor values received through `onSensorChanged()`.

For the light sensor, the first value in the event array represents the illumination in lux.

For the proximity sensor, the first value represents the measured proximity distance or detection state, depending on the sensor implementation.

The measurements are recorded in Logcat using the `C02_SENSORES` tag.

Example log messages:

```text
Medición de luz: 35.0 lux
Medición de proximidad: 5.0
```

The sensor accuracy changes are also recorded to help verify the sensor initialization process.

---

## 5. Experimental Tests

Several tests were performed to evaluate the sensor behavior under different conditions.

### 5.1. Light Sensor Measurements

The light sensor was tested under different illumination conditions.

The following measurements were observed:

| Environment | Approximate measured illumination |
|---|---:|
| Table | 35 lux |
| Well-lit area | 50 lux |
| Sensor partially covered | Around 5 lux |
| Sensor covered by a hand | 0–5 lux |
| Device inside a pocket | Around 0–2 lux |

The measurements demonstrate that the sensor responds to changes in ambient illumination.

The values are approximate and may vary depending on the environment, the position of the device, and the amount of light reaching the sensor.

The sensor does not necessarily report exactly 0 lux when covered. Small amounts of light may still reach the sensor through the surrounding material.

### 5.2. Proximity Sensor Measurements

The proximity sensor was tested by placing a hand or another object close to the sensor and then removing it.

The following behavior was observed:

| Situation | Measured value |
|---|---:|
| No object nearby | 5.0 |
| Hand close to the sensor | 0.0 |
| Sensor covered by a pocket | 0.0 |
| Device removed from the pocket | 5.0 |

The measurements show that the proximity sensor correctly detects nearby objects.

The sensor values are consistent with the behavior expected from the tested device.

### 5.3. Pocket Detection Test

The device was placed inside a pocket while the application remained open.

During the test, the following behavior was observed:

1. The light sensor initially reported values between approximately 14 and 32 lux.
2. As the device was placed inside the pocket, the measured illumination decreased.
3. The light sensor reported values close to 0 lux.
4. The proximity sensor changed from `5.0` to `0.0`.
5. When the device was removed from the pocket, the light measurements increased again.
6. The proximity sensor returned to `5.0`.

Example measurements:

```text
Medición de luz: 9.375 lux
Medición de proximidad: 0.0
Medición de luz: 3.25 lux
Medición de luz: 1.375 lux
```

After removing the device from the pocket:

```text
Medición de proximidad: 5.0
Medición de luz: 22.125 lux
Medición de luz: 37.625 lux
```

No `onPause()` or `onStop()` callback was observed in the corresponding measurement sequence.

Therefore, the application continued receiving sensor events while it remained in the foreground.

The observed behavior confirms that the sensors respond to the environmental changes caused by placing the device inside and removing it from a pocket.

### 5.4. Application Background and Foreground Test

The application was tested by switching to another application and then returning to AccessibleBusApp.

When the application was moved to the background, the following callbacks were observed:

```text
onPause(): Activity pausada
Listener desregistrado
onStop(): Activity deja de ser visible
```

When the application was opened again, the following callbacks were observed:

```text
onStart(): Activity vuelve a ser visible
onResume(): Activity en primer plano
```

Both sensor listeners were registered successfully:

```text
Registro sensor de luz: true
Registro sensor de proximidad: true
```

New sensor measurements were received after the Activity returned to the foreground.

This confirms that the application correctly unregisters its listeners when it is no longer visible and registers them again when it returns to the foreground.

### 5.5. Screen-Off and Unlock Test

The device screen was turned off, the application was left inactive for several seconds, and the device was then unlocked.

When the screen was turned off, the Activity received the following callbacks:

```text
onPause(): Activity pausada
Listener desregistrado
onStop(): Activity deja de ser visible
```

After unlocking the device, the Activity returned to the foreground:

```text
onStart(): Activity vuelve a ser visible
onResume(): Activity en primer plano
```

Both sensor listeners were successfully registered again.

The light sensor and proximity sensor resumed providing measurements.

This test confirms that the application can recover sensor acquisition after the device screen is turned off and subsequently unlocked.

The current implementation does not provide continuous sensor acquisition while the Activity is stopped.

---

## 6. Activity Lifecycle and Sensor Management

The application follows the Android Activity lifecycle to manage sensor registration.

The sensor acquisition process can be summarized as follows:

| Activity state | Sensor behavior |
|---|---|
| Activity created | Sensors are identified |
| Activity resumed | Sensor listeners are registered |
| Activity in foreground | Sensor events are received |
| Activity paused | Sensor listeners are unregistered |
| Activity stopped | No sensor events are received by this Activity |
| Activity resumed again | Sensor listeners are registered again |

This approach avoids keeping the sensor listeners registered when the Activity is not in the foreground.

It also ensures that the application can resume sensor acquisition when the user returns to the application.

---

## 7. Background Behavior and Limitations

The current implementation is designed for sensor acquisition while the application is in the foreground.

When the Activity is paused, the sensor listeners are unregistered.

Consequently, the application does not guarantee continuous sensor measurements while:

- The application is in the background.
- The device screen is off.
- The Activity is stopped.

The tests demonstrate that the application resumes sensor acquisition correctly when the Activity returns to the foreground.

Continuous background sensing would require a different implementation, such as an appropriate Android foreground service, together with the required permissions and notification handling.

This functionality is outside the scope of the current implementation.

---

## 8. Results

The experimental tests produced the following results:

| Test | Result |
|---|---|
| Sensor identification | Successful |
| Light sensor measurements | Successful |
| Proximity sensor measurements | Successful |
| Light changes when the sensor is covered | Successful |
| Proximity detection of nearby objects | Successful |
| Pocket detection behavior | Successful |
| Sensor listener unregistration when Activity pauses | Successful |
| Sensor listener registration when Activity resumes | Successful |
| Recovery after switching applications | Successful |
| Recovery after screen-off and unlock | Successful |

The tests confirm that both sensors are accessible and provide measurements under the tested conditions.

The application also handles the Activity lifecycle correctly by registering and unregistering the sensor listeners according to the foreground state.

---

## 9. Conclusion

The objective of this activity was to identify and test the light and proximity sensors available on an Android device and to implement sensor acquisition using the Android sensor framework.

The light sensor successfully measured ambient illumination in lux, showing lower values when covered and higher values when exposed to brighter environments.

The proximity sensor detected nearby objects, reporting `0.0` when an object was close and `5.0` when no object was detected within its range on the tested Motorola moto g35 5G.

The pocket test demonstrated that both sensors respond to environmental changes while the application remains in the foreground.

The application was also tested when switching between applications and when turning the screen off and unlocking the device. In both cases, sensor acquisition resumed successfully when the Activity returned to the foreground.

The current implementation therefore provides functional light and proximity sensor acquisition with appropriate Activity lifecycle management.

Continuous background acquisition is not implemented in this version.

Overall, the tests confirm that the sensor integration works correctly on the tested device and that the application can resume sensor acquisition after lifecycle transitions.