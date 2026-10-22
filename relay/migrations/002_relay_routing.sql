BEGIN;

CREATE TABLE relay_connections (
  device_id uuid PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
  connection_id uuid NOT NULL,
  expires_at timestamptz NOT NULL
);

CREATE TABLE relay_commands (
  id uuid PRIMARY KEY,
  device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  connection_id uuid NOT NULL,
  payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
  expects_reply boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  claimed_at timestamptz,
  result jsonb CHECK (result IS NULL OR jsonb_typeof(result) = 'object')
);
CREATE INDEX relay_commands_delivery ON relay_commands(device_id, connection_id, created_at) WHERE claimed_at IS NULL;
CREATE INDEX relay_commands_expiry ON relay_commands(expires_at);

CREATE TABLE relay_rate_limits (
  key text PRIMARY KEY,
  hits integer NOT NULL,
  expires_at timestamptz NOT NULL
);
CREATE INDEX relay_rate_limits_expiry ON relay_rate_limits(expires_at);

ALTER TABLE relay_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE relay_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE relay_rate_limits ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON relay_connections, relay_commands, relay_rate_limits FROM PUBLIC;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    REVOKE ALL ON relay_connections, relay_commands, relay_rate_limits FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    REVOKE ALL ON relay_connections, relay_commands, relay_rate_limits FROM authenticated;
  END IF;
END $$;

INSERT INTO schema_migrations(name) VALUES ('002_relay_routing.sql');
COMMIT;
