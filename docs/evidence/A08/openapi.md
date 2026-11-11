# A08 REST contract exported from FastAPI

Run on the laboratory VM (Ubuntu, Docker Compose) on a freshly started stack built from
this branch, which is `develop` after B05 plus the API description.

## Export

```text
$ bash api/tests/export_openapi.sh
Wrote /srv/a03-lab/contracts/openapi.yaml from http://localhost:8000
```

The exported contract has the 12 operations of the API, including `advance`, `end` and
`/health`. The hand-written version had 11 operations, and 8 of them had no documented
responses.

## Real responses against the exported contract

A script called every operation with the demo tokens, took the response schema for the
returned status from `contracts/openapi.yaml` and validated the body with JSON Schema
(2020-12). It also validated `contracts/samples/sync.json`.

```text
OK   reset: 200 cumple el contrato
OK   sync pasajero: 200 cumple el contrato
OK   sync conductor: 200 cumple el contrato
OK   paradas del viaje: 200 cumple el contrato
OK   crear: 201 cumple el contrato
OK   leer solicitud: 200 cumple el contrato
OK   aceptar: 200 cumple el contrato
OK   conflicto de versión: 409 cumple el contrato
OK   completar: 200 cumple el contrato
OK   crear otra: 201 cumple el contrato
OK   rechazar: 200 cumple el contrato
OK   crear tercera: 201 cumple el contrato
OK   cancelar: 200 cumple el contrato
OK   motivo inválido: 422 cumple el contrato
OK   pasar parada: 200 cumple el contrato
OK   sin permiso: 403 cumple el contrato
OK   terminar viaje: 200 cumple el contrato
OK   sin token: 401 cumple el contrato
OK   health: 200 cumple el contrato
OK   contracts/samples/sync.json: 200 cumple el contrato
OK   las 12 operaciones del contrato se probaron
TODO CORRECTO
```

The smoke test (`api/tests/smoke.sh`) also passed on the same stack.
