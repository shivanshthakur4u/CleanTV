BEGIN;

CREATE TABLE IF NOT EXISTS schema_migrations (
  name text PRIMARY KEY,
  applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE devices (
  id uuid PRIMARY KEY,
  token_hash text UNIQUE NOT NULL,
  owner_id uuid,
  name varchar(60) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen timestamptz
);
CREATE INDEX devices_owner ON devices(owner_id);

CREATE TABLE pairing_codes (
  code_hash text PRIMARY KEY,
  device_id uuid UNIQUE NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL
);

CREATE TABLE pairing_requests (
  id uuid PRIMARY KEY,
  device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  owner_id uuid NOT NULL,
  owner_email varchar(320) NOT NULL,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'declined')),
  expires_at timestamptz NOT NULL
);
CREATE INDEX pairing_requests_expiry ON pairing_requests(expires_at);

-- Mobile clients access these tables through the relay, never through the public data API.
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE pairing_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE pairing_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON devices, pairing_codes, pairing_requests, schema_migrations FROM PUBLIC;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    REVOKE ALL ON devices, pairing_codes, pairing_requests, schema_migrations FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    REVOKE ALL ON devices, pairing_codes, pairing_requests, schema_migrations FROM authenticated;
  END IF;
END $$;

INSERT INTO schema_migrations(name) VALUES ('001_devices.sql');
COMMIT;
