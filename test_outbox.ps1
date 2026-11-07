
$ErrorActionPreference = "Stop"

# PostgreSQL configuration
$Container = "postgres_service"
$DbUser = "accessiblebus"
$DbName = "accessiblebus"

# Test event identifiers
$EventId = "c0300000-0000-4000-8000-000000000002"
$RequestId = "c0300000-0000-4000-8000-000000000002"
$TripId = "c0300000-0000-4000-8000-000000000003"

# Isolated MQTT topic for the test
$Topic = "c03/test/event"

Write-Host ""
Write-Host "=== C03: Test de publicacion ===" -ForegroundColor Cyan
Write-Host ""

# --------------------------------------------------
# 1. Insert test event
# --------------------------------------------------

$TripId = "c0300000-0000-4000-8000-000000000003"
$EventId = "c0300000-0000-4000-8000-000000000002"

$Sql = @"
-- Crear el viaje sintético si todavía no existe.
INSERT INTO trip (
    id,
    vehicle_id,
    driver_subject,
    state
)
VALUES (
    '$TripId',
    'C03-TEST-BUS',
    'c03-test-driver',
    'ACTIVE'
)
ON CONFLICT (id) DO NOTHING;

-- Eliminar el evento de prueba anterior para repetir el test.
DELETE FROM outbox_event
WHERE event_id = '$EventId';

-- Insertar un evento válido para C03.
INSERT INTO outbox_event (
    event_id,
    event_type,
    request_id,
    trip_id,
    topic,
    payload
)
VALUES (
    '$EventId',
    'trip.progressed',
    NULL,
    '$TripId',
    'c03/test/event',
    '{
        "eventId": "$EventId",
        "eventType": "trip.progressed",
        "tripId": "$TripId",
        "message": "C03 outbox test"
    }'::jsonb
);
"@

$Sql | docker exec -i postgres_service psql -U accessiblebus -d accessiblebus
# --------------------------------------------------
# 2. Check initial state
# --------------------------------------------------

$InitialStateSql = @"
SELECT published_at IS NULL
FROM outbox_event
WHERE event_id = '$EventId';
"@

$InitialState = $InitialStateSql | docker exec -i $Container psql `
    -U $DbUser `
    -d $DbName `
    -t `
    -A `
    -v ON_ERROR_STOP=1

if ($LASTEXITCODE -ne 0) {
    throw "No se pudo consultar el estado inicial."
}

if ($InitialState.Trim() -ne "t") {
    Write-Host "El evento ya estaba publicado o no esta pendiente." `
        -ForegroundColor Yellow
}
else {
    Write-Host "PASS: El evento esta inicialmente pendiente." `
        -ForegroundColor Green
}

# --------------------------------------------------
# 3. Wait for published_at
# --------------------------------------------------

$TimeoutSeconds = 5
$Start = Get-Date
$Published = $false

Write-Host ""
Write-Host "Esperando a que el outbox publique el evento..."

while (((Get-Date) - $Start).TotalSeconds -lt $TimeoutSeconds) {

    $CheckSql = @"
SELECT published_at IS NOT NULL
FROM outbox_event
WHERE event_id = '$EventId';
"@

    $Result = $CheckSql | docker exec -i $Container psql `
        -U $DbUser `
        -d $DbName `
        -t `
        -A `
        -v ON_ERROR_STOP=1

    if ($LASTEXITCODE -ne 0) {
        throw "Error consultando PostgreSQL."
    }

    if ($Result.Trim() -eq "t") {
        $Published = $true
        break
    }

    Start-Sleep -Milliseconds 50
}

$ElapsedMs = [math]::Round(
    ((Get-Date) - $Start).TotalMilliseconds,
    2
)

# --------------------------------------------------
# 4. Report result
# --------------------------------------------------

Write-Host ""

if ($Published) {

    Write-Host "PASS: Evento marcado como publicado." `
        -ForegroundColor Green

    Write-Host "Tiempo hasta published_at: $ElapsedMs ms"

    if ($ElapsedMs -lt 1000) {
        Write-Host "PASS: Procesamiento inferior a 1 segundo." `
            -ForegroundColor Green
    }
    else {
        Write-Host "FAIL: Se supero el limite de 1 segundo." `
            -ForegroundColor Red
    }

}
else {

    Write-Host "FAIL: El evento no se publico en $TimeoutSeconds segundos." `
        -ForegroundColor Red

}

# --------------------------------------------------
# 5. Show final database state
# --------------------------------------------------

$FinalSql = @"
SELECT
    event_id,
    topic,
    payload,
    created_at,
    published_at
FROM outbox_event
WHERE event_id = '$EventId';
"@

Write-Host ""
Write-Host "Estado final en PostgreSQL:"
Write-Host ""

$FinalSql | docker exec -i $Container psql `
    -U $DbUser `
    -d $DbName `
    -v ON_ERROR_STOP=1

if ($LASTEXITCODE -ne 0) {
    throw "No se pudo consultar el estado final."
}

Write-Host ""
Write-Host "=== Fin de la prueba C03 ===" -ForegroundColor Cyan
