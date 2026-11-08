# A09 unit tests

`./gradlew :app:testDebugUnitTest`, with `API_URL` pointing at the laboratory API so the
live test runs too:

```text
API_URL=http://192.168.1.172:8000 ./gradlew :app:testDebugUnitTest --rerun
```

| Suite | Tests | Failures | What it checks |
|---|---|---|---|
| `ModelsTest` | 4 | 0 | `sync.json` and the four event samples decode into the K5 models; unknown fields are ignored |
| `SampleApiTest` | 2 | 0 | The offline source follows the API rules: repeated keys, versions, transitions, passing a stop, ending the trip |
| `ApiClientTest` | 7 | 0 | Against MockWebServer: path, token, `Idempotency-Key` and body; 409 with and without `currentResource`; 5xx and no connection are retryable; 403 and 422 are not |
| `ApiClientLiveTest` | 1 | 0 | Against the running API: reset, create, repeat, idempotency conflict, accept, stale cancel, complete, `/sync` for passenger and driver, stop visits, 403 |
| `ExampleUnitTest` | 1 | 0 | Template test |

The live test found that the create body left out `assistanceType`, because
kotlinx.serialization does not write default values. The bodies now have no defaults and
`createSendsTheWholeBody` checks the exact JSON.

Without `API_URL`, `ApiClientLiveTest` is skipped and the other 14 tests need no network.
