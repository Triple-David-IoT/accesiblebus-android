# B11 — Automatic HMI receipts

## Configuration

- Android debug build using the real API (`useSampleApi=false`).
- API and Mosquitto running in the local Docker Compose stack.
- Vehicle: `BUS-DEMO-01`.
- Terminal and MQTT client ID: `hmi-demo-01`.
- Receipt topic: `terminals/hmi-demo-01/receipts`.
- Receipt QoS: 1; retain: false.

## Automatic receipt

1. Open the Driver HMI.
2. Create an assistance request through REST with the passenger token.
3. Observe the HMI receipt and the API's `request.receipt_recorded` event.
4. Check that the HMI displays the request and "Received by terminal".

Evidence:
- `01-automatic-receipt.jpg`
- `02-hmi-live.jpg`

## Recovery through /sync after an API restart

1. Force-stop the Android app so the HMI is not connected.
2. Create a pending assistance request through REST.
3. Verify that the request has `received: false`.
4. Restart the API and wait for `/health` to respond successfully.
5. Open the Driver HMI. If necessary, press Refresh.
6. Verify that the same request now has `received: true`.
7. Observe its receipt and `request.receipt_recorded` event in MQTT.

Result: the HMI recovered the request through /sync and acknowledged it.

Evidence:
- `03-recovery-before.jpg`
- `04-recovery-after.jpg`
- `05-recovery-mqtt.jpg`

## Scope of this test

This verifies recovery of a request created while the HMI was absent,
after an API restart. It does not simulate a receipt lost in flight.

No database rows were deleted in this successful recovery test.