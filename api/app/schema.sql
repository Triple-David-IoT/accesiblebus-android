-- Assistance service schema. The API applies this file on every startup.
-- CREATE TABLE IF NOT EXISTS does not alter existing tables: once the schema
-- is shared, add changes in a new schema_002.sql file.

CREATE TABLE IF NOT EXISTS trip (
  id                   UUID PRIMARY KEY,
  vehicle_id           TEXT NOT NULL,
  driver_subject       TEXT NOT NULL,
  state                TEXT NOT NULL CHECK (state IN ('ACTIVE', 'ENDED')),
  last_passed_sequence INTEGER NOT NULL DEFAULT 0 CHECK (last_passed_sequence >= 0),
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS one_active_trip_per_vehicle
  ON trip (vehicle_id)
  WHERE state = 'ACTIVE';

-- A visit is one trip's pass through a physical stop. A reset creates new
-- visits over the same CRTM stop codes (CODIGOESTACION, which is text).
CREATE TABLE IF NOT EXISTS stop_visit (
  id                 UUID PRIMARY KEY,
  trip_id            UUID NOT NULL REFERENCES trip (id),
  sequence           INTEGER NOT NULL CHECK (sequence >= 1),
  physical_stop_code TEXT NOT NULL,
  UNIQUE (trip_id, sequence)
);

-- "received" is not stored: it is true when a matching terminal_receipt exists.
-- The vehicle comes from stop_visit -> trip.
CREATE TABLE IF NOT EXISTS assistance_request (
  id               UUID PRIMARY KEY,
  owner_subject    TEXT NOT NULL,
  stop_visit_id    UUID NOT NULL REFERENCES stop_visit (id),
  assistance_type  TEXT NOT NULL DEFAULT 'RAMP_BOARDING'
                   CHECK (assistance_type = 'RAMP_BOARDING'),
  state            TEXT NOT NULL CHECK (state IN (
                     'PENDING', 'ACCEPTED', 'REJECTED',
                     'CANCELLED', 'COMPLETED', 'EXPIRED'
                   )),
  version          INTEGER NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_event_id UUID NOT NULL UNIQUE,
  close_reason     TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (state <> 'REJECTED' OR close_reason IS NOT NULL),
  CHECK (state <> 'EXPIRED' OR close_reason IN ('STOP_PASSED', 'TRIP_ENDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS one_active_request_per_visit
  ON assistance_request (owner_subject, stop_visit_id)
  WHERE state IN ('PENDING', 'ACCEPTED');

-- The key is reserved before the command runs, so response starts as NULL and
-- is filled before the transaction commits.
CREATE TABLE IF NOT EXISTS processed_command (
  actor           TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  operation       TEXT NOT NULL,
  path            TEXT NOT NULL,
  command_hash    TEXT NOT NULL,
  response        JSONB,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (actor, idempotency_key)
);

-- One row per event and destination topic. payload is the full MQTT message,
-- ready to publish. seq sets the publishing order, because rows written in the
-- same transaction share created_at.
CREATE TABLE IF NOT EXISTS outbox_event (
  seq          BIGINT GENERATED ALWAYS AS IDENTITY,
  event_id     UUID NOT NULL,
  event_type   TEXT NOT NULL CHECK (event_type IN (
                 'request.created', 'request.accepted', 'request.rejected',
                 'request.cancelled', 'request.completed', 'request.expired',
                 'request.receipt_recorded', 'trip.progressed', 'trip.ended'
               )),
  request_id   UUID REFERENCES assistance_request (id),
  trip_id      UUID NOT NULL REFERENCES trip (id),
  topic        TEXT NOT NULL,
  payload      JSONB NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  PRIMARY KEY (event_id, topic),
  CHECK ((event_type LIKE 'request.%') = (request_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS terminal_receipt (
  created_event_id UUID NOT NULL REFERENCES assistance_request (created_event_id),
  request_id       UUID NOT NULL REFERENCES assistance_request (id),
  terminal_id      TEXT NOT NULL,
  received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (created_event_id, terminal_id)
);
