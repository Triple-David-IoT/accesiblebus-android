# A08 smoke test

Run on the laboratory VM (Ubuntu, Docker Compose) on a freshly started stack:
`docker compose down -v`, then `postgres`, `mosquitto` and `api` up, then
`api/tests/smoke.sh` twice in a row.

## First run

```text
Smoke test against http://localhost:8000 (run smoke-1794138279-390084)
OK: health (HTTP 200)
OK: operator resets the demo trip (HTTP 200)
OK: passenger creates a request (HTTP 201)
OK: repeating the same key returns the stored request (HTTP 200)
OK: same key with another body is a conflict (HTTP 409)
OK: driver accepts (HTTP 200)
OK: driver completes (HTTP 200)
OK: passenger reads /sync (HTTP 200)
OK: driver reads /sync (HTTP 200)
OK: operator reads /sync (HTTP 200)
Smoke test passed
```

## Second run, same stack

The script resets the demo trip and uses new idempotency keys on every run, so it
can be repeated without restarting anything.

```text
Smoke test against http://localhost:8000 (run smoke-1794138279-390113)
OK: health (HTTP 200)
OK: operator resets the demo trip (HTTP 200)
OK: passenger creates a request (HTTP 201)
OK: repeating the same key returns the stored request (HTTP 200)
OK: same key with another body is a conflict (HTTP 409)
OK: driver accepts (HTTP 200)
OK: driver completes (HTTP 200)
OK: passenger reads /sync (HTTP 200)
OK: driver reads /sync (HTTP 200)
OK: operator reads /sync (HTTP 200)
Smoke test passed
```

## Failure is detected

With no API listening, the script stops at the first step with exit code 1:

```text
$ API_URL=http://localhost:9 bash api/tests/smoke.sh
Smoke test against http://localhost:9 (run smoke-1794138279-390142)
FAIL: could not reach http://localhost:9
```

After both runs, the outbox had published every event it wrote (12 of 12).
