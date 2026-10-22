import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import pg from 'pg';

export const fail = (status: number, message: string) => Object.assign(new Error(message), { statusCode: status });
export type CommandResult = { ok: true; state: unknown } | { ok: false; error: string; status?: number };
type Delivery = { id: string; payload: Record<string, unknown>; expires_at: Date };

export class RelayBroker {
  private listener: pg.Client | null = null;
  private starting: Promise<void> | null = null;
  constructor(private pool: pg.Pool, private connectionString: string, private wake: (device: string) => void, private lost: () => void) {}

  async listen() {
    if (this.starting) return this.starting;
    const client = new pg.Client({ connectionString: this.connectionString, connectionTimeoutMillis: 5000 });
    this.listener = client;
    client.on('notification', event => {
      if (event.channel === 'tvhome_relay' && /^[a-f0-9-]{36}$/.test(event.payload ?? '')) this.wake(event.payload!);
    });
    const disconnected = () => {
      if (this.listener !== client) return;
      this.listener = null; this.starting = null;
      void client.end().catch(() => {});
      this.lost();
    };
    client.on('error', disconnected); client.on('end', disconnected);
    this.starting = (async () => { await client.connect(); await client.query('LISTEN tvhome_relay'); })();
    try { await this.starting; }
    catch (error) { disconnected(); throw error; }
  }
  async stopListening() {
    const client = this.listener;
    this.listener = null; this.starting = null;
    if (client) await client.end().catch(() => {});
  }
  async notify(device: string) { await this.pool.query("SELECT pg_notify('tvhome_relay',$1)", [device]); }
  async register(device: string, connection: string) {
    await this.pool.query("INSERT INTO relay_connections(device_id,connection_id,expires_at) VALUES($1,$2,now()+interval '40 seconds') ON CONFLICT(device_id) DO UPDATE SET connection_id=$2,expires_at=EXCLUDED.expires_at", [device, connection]);
    await this.pool.query('UPDATE devices SET last_seen=now() WHERE id=$1', [device]);
    await this.notify(device);
  }
  async current(device: string) {
    const result = await this.pool.query<{ connection_id: string | null }>('SELECT c.connection_id FROM devices d LEFT JOIN relay_connections c ON c.device_id=d.id WHERE d.id=$1', [device]);
    return result.rows[0];
  }
  async renew(device: string, connection: string) {
    const result = await this.pool.query("WITH live AS (UPDATE relay_connections SET expires_at=now()+interval '40 seconds' WHERE device_id=$1 AND connection_id=$2 RETURNING device_id) UPDATE devices SET last_seen=now() WHERE id IN(SELECT device_id FROM live) RETURNING id", [device, connection]);
    return result.rowCount === 1;
  }
  async release(device: string, connection: string) {
    await this.pool.query('DELETE FROM relay_connections WHERE device_id=$1 AND connection_id=$2', [device, connection]);
    await this.pool.query("UPDATE relay_commands SET result=$3::jsonb WHERE device_id=$1 AND connection_id=$2 AND expects_reply AND result IS NULL", [device, connection, JSON.stringify({ ok: false, error: 'TV disconnected before confirming the change.', status: 409 })]);
  }
  async enqueue(device: string, payload: Record<string, unknown>, expectsReply: boolean, id = randomUUID()) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const live = await client.query<{ connection_id: string }>('SELECT connection_id FROM relay_connections WHERE device_id=$1 AND expires_at>now() FOR UPDATE', [device]);
      if (!live.rows[0]) throw fail(409, 'TV is offline. Turn it on and check its internet connection.');
      if (expectsReply) {
        const active = await client.query("SELECT count(*)::integer AS count FROM relay_commands WHERE device_id=$1 AND expects_reply AND result IS NULL AND expires_at>now()", [device]);
        if (active.rows[0].count >= 4) throw fail(429, 'Wait for the current TV command to finish.');
      }
      const expiry = new Date(Date.now() + (expectsReply ? 14000 : 120000));
      await client.query('INSERT INTO relay_commands(id,device_id,connection_id,payload,expects_reply,expires_at) VALUES($1,$2,$3,$4::jsonb,$5,$6)', [id, device, live.rows[0].connection_id, JSON.stringify(payload), expectsReply, expiry]);
      await client.query("SELECT pg_notify('tvhome_relay',$1)", [device]);
      await client.query('COMMIT');
      return id;
    } catch (error) { await client.query('ROLLBACK'); throw error; }
    finally { client.release(); }
  }
  async claim(device: string, connection: string) {
    const result = await this.pool.query<Delivery>("UPDATE relay_commands SET claimed_at=now() WHERE id IN (SELECT id FROM relay_commands WHERE device_id=$1 AND connection_id=$2 AND claimed_at IS NULL AND expires_at>now() AND EXISTS(SELECT 1 FROM relay_connections WHERE device_id=$1 AND connection_id=$2 AND expires_at>now()) AND (expects_reply OR EXISTS(SELECT 1 FROM pairing_requests p WHERE p.id=relay_commands.id AND p.status='pending' AND p.expires_at>now())) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 4) RETURNING id,payload,expires_at", [device, connection]);
    return result.rows;
  }
  async complete(device: string, connection: string, id: string, result: CommandResult) {
    await this.pool.query('UPDATE relay_commands SET result=$4::jsonb WHERE id=$1 AND device_id=$2 AND connection_id=$3 AND expects_reply AND claimed_at IS NOT NULL AND result IS NULL AND expires_at>now() AND EXISTS(SELECT 1 FROM relay_connections WHERE device_id=$2 AND connection_id=$3)', [id, device, connection, JSON.stringify(result)]);
  }
  async send(device: string, command: unknown): Promise<unknown> {
    const id = await this.enqueue(device, { type: 'command', command }, true);
    const deadline = Date.now() + 15000;
    try {
      while (Date.now() < deadline) {
        const result = await this.pool.query<{ result: CommandResult | null }>('SELECT result FROM relay_commands WHERE id=$1', [id]);
        if (!result.rows[0]) throw fail(409, 'This TV has been disconnected. Pair it again.');
        const reply = result.rows[0].result;
        if (reply?.ok) return reply.state;
        if (reply && !reply.ok) throw fail(reply.status ?? 422, reply.error);
        await delay(150);
      }
      throw fail(504, 'The TV did not confirm this change. Refresh before trying again.');
    } finally { await this.pool.query('DELETE FROM relay_commands WHERE id=$1', [id]).catch(() => {}); }
  }
  async cleanup() {
    await this.pool.query("DELETE FROM relay_commands WHERE expires_at<now()-interval '1 minute'; DELETE FROM relay_connections WHERE expires_at<now(); DELETE FROM relay_rate_limits WHERE expires_at<now(); DELETE FROM pairing_requests WHERE expires_at<now()-interval '1 day'; DELETE FROM pairing_codes WHERE expires_at<now(); DELETE FROM devices WHERE owner_id IS NULL AND created_at<now()-interval '7 days'");
  }
}
