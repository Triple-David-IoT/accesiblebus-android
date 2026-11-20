# Test Report — AccesibleBusApp
## C10 · Orientation, request submission and idempotency

**Overall result:** the unit test suite completed successfully (`BUILD SUCCESSFUL`). The reviewed manual-test log excerpts showed no duplicate requests within the excerpts provided.

## 1. Confirm screen — rotation during submission

A request was submitted from the **Confirm** screen. The device was rotated while the sending state was displayed.

```text
POST /requests HTTP/1.1" 201 Created
...
outbox.publish.started event_id=b5e08bb9-52f1-49aa-be11-7e34291e9922 ...
outbox.publish.confirmed ... users/passenger-1/events
outbox.publish.confirmed ... vehicles/BUS-DEMO-01/events
receipts.recorded event_id=b5e08bb9-52f1-49aa-be11-7e34291e9922 terminal=hmi-demo-01
```

**Observation:** one request creation (`201 Created`) and publication of its event are visible. No second `POST /requests` appears in the reviewed excerpt. The device was rotated while the request was being submitted.

## 2. Tracking screen — orientation change

The **Tracking** screen was opened and the device was rotated between portrait and landscape. The user reported that the screen remained visually unchanged.

```text
POST /requests HTTP/1.1" 201 Created
GET /sync?scope=mine HTTP/1.1" 200 OK
outbox.publish.started event_id=b2e030b0-2b61-4c4a-9e16-25f0f2d4a55e ...
outbox.publish.confirmed ... users/passenger-1/events
outbox.publish.confirmed ... vehicles/BUS-DEMO-01/events
receipts.recorded event_id=b2e030b0-2b61-4c4a-9e16-25f0f2d4a55e terminal=hmi-demo-01
```

**Observation:** the excerpt records one request creation, a successful synchronization (`200 OK`) and event publication. No second creation appears. The log alone does not establish that a request was pending at the exact moment of rotation.

## 3. HMI — acceptance and rotation

The **Accept** action was performed in the HMI, and the device was rotated.

```text
POST /requests/0a50fef8-93f7-435a-ad5e-6faca1e9479e/accept HTTP/1.1" 200 OK
outbox.publish.started event_id=7b640936-4792-4611-9384-0617d793a6bd topic=users/passenger-1/events
outbox.publish.confirmed ...
outbox.publish.started ... topic=vehicles/BUS-DEMO-01/events
outbox.publish.confirmed ...
```

**Observation:** the acceptance request returned successfully (`200 OK`), and the corresponding events were published. No second `accept` request appears in the reviewed excerpt.

## 4. Automated API and idempotency tests

The following tests were reviewed in the project:

| File / test | What it checks |
|---|---|
| `ApiClientTest.kt` — `commandSendsTokenKeyAndBody()` | Verifies that the `Idempotency-Key` header is sent with the expected value. |
| `ApiClientTest.kt` — `createSendsTheWholeBody()` | Verifies the request body sent when creating a request. |
| `AssistanceRepositoryTest.kt` — `apiDownThenRetrySendsOneRequestWithTheSameKey()` | Checks that a retry uses the same key and results in only one request on the mock server. |
| `AssistanceRepositoryTest.kt` — `lostAnswerIsRecoveredWithTheSameKey()` | Checks recovery after a lost response without creating a second request. |
| `AssistanceRepositoryTest.kt` — `keysLeftByAPreviousRunAreReused()` | Checks that keys retained between repository instances are reused. |

`ApiClientLiveTest.kt` was also identified. It runs tests against a real API when `API_URL` is configured. Its `mainFlow()` creates a request, repeats the operation with the same key, and checks for an idempotency conflict when that key is reused with a different `stop`. **It was not run as part of the command shown below.** It also contains a `resetDemo` operation, so it should not be run against a demo environment without first considering its effects.

## 5. Gradle test result

Command executed:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Final output:

```text
BUILD SUCCESSFUL in 16s
26 actionable tasks: 9 executed, 17 up-to-date
Configuration cache entry stored.
```

The test compilation produced warnings, but no errors:

```text
w: .../AssistanceRepositoryTest.kt:150:32 Unnecessary non-null assertion (!!) on a non-null receiver of type 'Trip'.
w: .../AssistanceRepositoryTest.kt:154:47 Unnecessary non-null assertion (!!) on a non-null receiver of type 'Trip'.
w: .../RepositoryEventsTest.kt:84:9 This declaration needs opt-in. Its usage should be marked with '@kotlinx.coroutines.ExperimentalCoroutinesApi' or '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'
w: .../RepositoryEventsTest.kt:113:9 This declaration needs opt-in. Its usage should be marked with '@kotlinx.coroutines.ExperimentalCoroutinesApi' or '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'
w: .../RepositoryEventsTest.kt:123:9 This declaration needs opt-in. Its usage should be marked with '@kotlinx.coroutines.ExperimentalCoroutinesApi' or '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'
w: .../RepositoryEventsTest.kt:153:9 This declaration needs opt-in. Its usage should be marked with '@kotlinx.coroutines.ExperimentalCoroutinesApi' or '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'
w: .../RepositoryEventsTest.kt:172:9 This declaration needs opt-in. Its usage should be marked with '@kotlinx.coroutines.ExperimentalCoroutinesApi' or '@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)'
w: .../TrackingFactsTest.kt:77:67 Unnecessary non-null assertion (!!) on a non-null receiver of type 'Trip'.
```

**Interpretation:** the module's unit tests completed successfully. The warnings listed above did not prevent execution.

## 6. Additional historical logs

Earlier in the conversation, additional accumulated logs were shared, including `POST /requests`, `cancel`, `accept`, `complete` and `advance` operations, MQTT event publications, and `receipts.duplicate` entries.

The complete raw text of that historical log block is not available in the current record, so individual lines or counts are not reproduced here. In particular, `receipts.duplicate` indicates receipt deduplication; by itself, it does **not** prove that duplicate commands were absent during device rotation.

## 7. Conclusion

- The `:app:testDebugUnitTest` suite completed with **BUILD SUCCESSFUL**.
- The reviewed tests cover the idempotency header, key persistence and reuse, and retry behavior.
- The available backend excerpts show one request creation, a successful synchronization, and a successful HMI acceptance. No second corresponding request is visible in those excerpts.
- Rotation was tested manually on the Confirm and Tracking screens; the Tracking screen remained visually unchanged.
- The available logs support the observed behavior, but do not constitute exhaustive proof that duplicates cannot occur in every rotation scenario.

**Additional evidence to attach:** the four orientation screenshots and, if available, the video showing rotation during request submission.
