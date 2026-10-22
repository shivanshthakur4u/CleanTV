import Fastify, { type FastifyRequest } from 'fastify';
import rateLimit from '@fastify/rate-limit';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { readFile, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import pg from 'pg';
import { WebSocket, WebSocketServer } from 'ws';
import { z } from 'zod';
import { RelayBroker, fail } from './broker.js';
import { databaseRateStore, incrementRate } from './rate-store.js';
import { databaseConnectionString } from './database.js';

export const config = { maxDuration: 300 };
// Keep the database CA visible to Vercel's file tracer and verify it is bundled.
if (process.env.VERCEL === '1') await readFile(new URL('../certs/prod-ca-2021.crt', import.meta.url));

const env = z.object({
  DATABASE_URL: z.string().min(1),
  SUPABASE_URL: z.url().refine(v => new URL(v).protocol === 'https:'),
  AUTH_AUDIENCE: z.string().default('authenticated'),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  HOST: z.string().default('127.0.0.1'),
  TRUST_PROXY: z.enum(['true', 'false']).default('false'),
}).parse(process.env);
const issuer = `${env.SUPABASE_URL.replace(/\/$/, '')}/auth/v1`;
const jwks = createRemoteJWKSet(new URL(`${issuer}/.well-known/jwks.json`));
const connectionString = databaseConnectionString(env.DATABASE_URL);
const pool = new pg.Pool({ connectionString, max: 4, idleTimeoutMillis: 10000, connectionTimeoutMillis: 5000, statement_timeout: 10000 });
const app = Fastify({ bodyLimit: 3_000_000, trustProxy: process.env.VERCEL === '1' || env.TRUST_PROXY === 'true', logger: { redact: ['req.headers.authorization', 'req.body'] } });
pool.on('error', () => app.log.warn('Idle database connection closed.'));
await app.register(rateLimit, { max: 120, timeWindow: '1 minute', store: databaseRateStore(pool) });

const hash = (value: string) => createHash('sha256').update(value).digest('hex');
const uuid = z.uuid();
const pkg = z.string().regex(/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/).max(200);
const settings = z.object({
  scene: z.enum(['dusk', 'aurora', 'sand', 'ocean', 'photo']).optional(),
  dim: z.number().min(0).max(0.8).optional(),
  roomName: z.string().trim().min(1).max(40).optional(),
  favorites: z.array(pkg).max(8).refine(v => new Set(v).size === v.length).optional(),
  clock24h: z.boolean().optional(),
}).strict();
const command = z.discriminatedUnion('type', [
  z.object({ type: z.literal('getState') }).strict(),
  z.object({ type: z.literal('settings'), settings }).strict(),
  z.object({ type: z.literal('wallpaper'), data: z.string().min(4).max(2_800_000).regex(/^[A-Za-z0-9+/]+={0,2}$/) }).strict(),
  z.object({ type: z.literal('launchApp'), package: pkg }).strict(),
  z.object({ type: z.literal('navigate'), key: z.enum(['up', 'down', 'left', 'right', 'select', 'back', 'home']) }).strict(),
]);
const tvState = z.object({
  roomName: z.string().max(40), scene: z.enum(['dusk', 'aurora', 'sand', 'ocean', 'photo']),
  dim: z.number().min(0).max(0.8), clock24h: z.boolean(),
  favorites: z.array(pkg).max(8),
  apps: z.array(z.object({ package: pkg, label: z.string().max(160) })).max(300),
  model: z.string().max(160), sdk: z.number().int(),
  homeVisible: z.boolean(), wallpaperPreview: z.string().max(160000).optional(),
}).strict();
type Device = { id: string; owner_id: string | null; name: string };
type Connection = { ws: WebSocket; id: string; syncing: boolean; again: boolean };
const sockets = new Map<string, Connection>();
let upgrades = 0;
const broker = new RelayBroker(pool, connectionString, id => { void syncDevice(id); }, () => {
  for (const connection of sockets.values()) connection.ws.close(1012, 'Relay reconnecting');
});
async function syncDevice(id: string) {
  const entry = sockets.get(id);
  if (!entry) return;
  if (entry.syncing) { entry.again = true; return; }
  entry.syncing = true;
  try {
    do {
      entry.again = false;
      const current = await broker.current(id);
      if (!current) { entry.ws.close(4001, 'Access revoked'); return; }
      if (current.connection_id !== entry.id) { entry.ws.close(4000, 'Reconnected'); return; }
      const deliveries = await broker.claim(id, entry.id);
      for (const delivery of deliveries) {
        if (sockets.get(id) !== entry || entry.ws.readyState !== WebSocket.OPEN) return;
        entry.ws.send(JSON.stringify({ ...delivery.payload, requestId: delivery.id, expiresAt: delivery.expires_at.getTime() }), error => {
          if (error) void broker.complete(id, entry.id, delivery.id, { ok: false, error: 'TV connection interrupted.', status: 503 }).catch(() => {});
        });
      }
      if (deliveries.length === 4) entry.again = true;
    } while (entry.again && sockets.get(id) === entry && entry.ws.readyState === WebSocket.OPEN);
  } catch { entry.ws.close(1011, 'Relay unavailable'); }
  finally { entry.syncing = false; }
}
let nextCleanup = 0;
app.addHook('onRequest', async req => {
  if (Date.now() < nextCleanup) return;
  nextCleanup = Date.now() + 60000;
  try { await broker.cleanup(); }
  catch (error) {
    nextCleanup = 0;
    const failure = error as { code?: unknown; cause?: { code?: unknown }; errors?: { code?: unknown }[] };
    const code = (value: unknown) => typeof value === 'string' && /^[A-Z0-9_]{1,64}$/.test(value) ? value : 'UNKNOWN';
    req.log.error({
      code: code(failure?.code), causeCode: code(failure?.cause?.code),
      nestedCodes: Array.isArray(failure?.errors) ? failure.errors.slice(0, 4).map(item => code(item?.code)) : [],
      timedOut: error instanceof Error && /timeout|timed out/i.test(error.message),
      tlsModeSpecified: /[?&]sslmode=/.test(env.DATABASE_URL),
      tlsCaSpecified: /[?&]sslrootcert=/.test(env.DATABASE_URL),
    }, '[DEBUG-db-connect] Database cleanup failed');
    throw fail(503, 'Database is temporarily unavailable.');
  }
});

async function owner(req: FastifyRequest) {
  const token = req.headers.authorization?.match(/^Bearer (\S+)$/)?.[1];
  if (!token) throw fail(401, 'Sign in to continue.');
  try {
    const { payload } = await jwtVerify(token, jwks, { issuer, audience: env.AUTH_AUDIENCE, algorithms: ['ES256', 'RS256'] });
    if (!uuid.safeParse(payload.sub).success || typeof payload.email !== 'string' || payload.is_anonymous === true) throw new Error();
    return { id: payload.sub!, email: payload.email.slice(0, 320) };
  } catch { throw fail(401, 'Your session has expired. Sign in again.'); }
}
async function tv(authorization: string | undefined): Promise<Device> {
  const token = authorization?.match(/^Device ([a-f0-9]{64})$/)?.[1];
  if (!token) throw fail(401, 'TV authorization required.');
  const result = await pool.query<Device>('SELECT id, owner_id, name FROM devices WHERE token_hash = $1', [hash(token)]);
  if (!result.rows[0]) throw fail(401, 'This TV has been disconnected. Pair it again.');
  return result.rows[0];
}
async function owned(req: FastifyRequest) {
  const user = await owner(req);
  const id = uuid.parse((req.params as { id: string }).id);
  const result = await pool.query<Device>('SELECT id, owner_id, name FROM devices WHERE id = $1 AND owner_id = $2', [id, user.id]);
  if (!result.rows[0]) throw fail(404, 'TV not found.');
  return result.rows[0];
}

app.setErrorHandler((error, req, reply) => {
  if (error instanceof z.ZodError) return reply.code(400).send({ error: 'Invalid request. Check the values and try again.' });
  const status = (error as { statusCode?: number }).statusCode ?? 500;
  if (status >= 500) req.log.error({ err: error }, 'Request failed');
  return reply.code(status).send({ error: status === 500 ? 'The service could not complete this request.' : (error as Error).message });
});
app.addHook('onSend', async (_req, reply) => {
  reply.header('X-Content-Type-Options', 'nosniff').header('Cache-Control', 'no-store').header('Referrer-Policy', 'no-referrer');
});
app.get('/health', async () => { await pool.query('SELECT 1'); return { status: 'ok' }; });
app.get('/', async (_req, reply) => reply.type('text/html').send(await readFile(new URL('../public/index.html', import.meta.url), 'utf8')));
for (const name of ['tv-home-preview.apk', 'tv-home-companion-preview.apk']) app.get(`/downloads/${name}`, async (_req, reply) => {
  const path = new URL(`../public/downloads/${name}`, import.meta.url);
  try {
    const file = await stat(path);
    return reply.type('application/vnd.android.package-archive').header('Content-Length', file.size).header('Content-Disposition', `attachment; filename="${name}"`).send(createReadStream(path));
  } catch { return reply.code(503).send({ error: 'No preview APK has been published on this server yet.' }); }
});
app.get('/v1/devices', async req => {
  const user = await owner(req);
  const result = await pool.query('SELECT d.id,d.name,d.last_seen,(c.expires_at>now()) IS TRUE AS online FROM devices d LEFT JOIN relay_connections c ON c.device_id=d.id WHERE d.owner_id=$1 ORDER BY d.created_at', [user.id]);
  return { devices: result.rows };
});
app.get('/v1/devices/:id/state', async req => broker.send((await owned(req)).id, { type: 'getState' }));
app.post('/v1/devices/:id/command', async req => {
  const device = await owned(req);
  const payload = command.parse(req.body);
  const state = await broker.send(device.id, payload);
  if (payload.type === 'settings' && payload.settings.roomName) await pool.query('UPDATE devices SET name=$1 WHERE id=$2', [payload.settings.roomName, device.id]);
  return state;
});
app.delete('/v1/devices/:id', async req => {
  const device = await owned(req);
  await pool.query('DELETE FROM devices WHERE id=$1', [device.id]);
  await broker.notify(device.id);
  return { removed: true };
});

app.post('/v1/tv/register', { config: { rateLimit: { max: 10, timeWindow: '1 hour' } } }, async req => {
  const { name } = z.object({ name: z.string().trim().min(1).max(60) }).strict().parse(req.body);
  const id = randomUUID(), token = randomBytes(32).toString('hex');
  await pool.query('INSERT INTO devices(id, token_hash, name) VALUES($1,$2,$3)', [id, hash(token), name]);
  return { id, token };
});
app.post('/v1/tv/pairing', { config: { rateLimit: { max: 15, timeWindow: '15 minutes' } } }, async req => {
  const device = await tv(req.headers.authorization);
  if (device.owner_id) throw fail(409, 'This TV is already paired. Disconnect it before changing its owner.');
  const code = randomBytes(5).toString('hex').toUpperCase();
  await pool.query('INSERT INTO pairing_codes(code_hash,device_id,expires_at) VALUES($1,$2,now()+interval \'5 minutes\') ON CONFLICT(device_id) DO UPDATE SET code_hash=EXCLUDED.code_hash, expires_at=EXCLUDED.expires_at', [hash(code), device.id]);
  return { code, expiresIn: 300 };
});
app.post('/v1/tv/disconnect', async req => {
  const device = await tv(req.headers.authorization);
  await pool.query('DELETE FROM devices WHERE id=$1', [device.id]);
  await broker.notify(device.id);
  return { removed: true };
});
app.post('/v1/pair', { config: { rateLimit: { max: 8, timeWindow: '15 minutes' } } }, async req => {
  const user = await owner(req);
  const { code } = z.object({ code: z.string().regex(/^[A-F0-9]{10}$/) }).strict().parse(req.body);
  const result = await pool.query('SELECT d.id FROM pairing_codes p JOIN devices d ON d.id=p.device_id WHERE code_hash=$1 AND expires_at>now() AND d.owner_id IS NULL', [hash(code)]);
  const device = result.rows[0];
  if (!device) throw fail(400, 'Pairing code is invalid or expired. Get a new code on the TV.');
  const id = randomUUID();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const locked = await client.query('SELECT id FROM devices WHERE id=$1 AND owner_id IS NULL FOR UPDATE', [device.id]);
    if (!locked.rowCount) throw fail(409, 'This TV has already been paired.');
    await client.query("UPDATE pairing_requests SET status='declined' WHERE device_id=$1 AND status='pending'", [device.id]);
    await client.query("INSERT INTO pairing_requests(id,device_id,owner_id,owner_email,expires_at) VALUES($1,$2,$3,$4,now()+interval '2 minutes')", [id, device.id, user.id, user.email]);
    await client.query('COMMIT');
  } catch (error) { await client.query('ROLLBACK'); throw error; }
  finally { client.release(); }
  await broker.enqueue(device.id, { type: 'pairRequest', email: user.email }, false, id);
  return { requestId: id, status: 'pending' };
});
app.get('/v1/pair/:id', async req => {
  const user = await owner(req);
  const id = uuid.parse((req.params as { id: string }).id);
  const result = await pool.query('SELECT status, expires_at, device_id FROM pairing_requests WHERE id=$1 AND owner_id=$2', [id, user.id]);
  const row = result.rows[0];
  if (!row) throw fail(404, 'Pairing request not found.');
  return { status: row.status === 'pending' && new Date(row.expires_at).getTime() < Date.now() ? 'expired' : row.status, deviceId: row.device_id };
});
app.delete('/v1/pair/:id', async req => {
  const user = await owner(req);
  const id = uuid.parse((req.params as { id: string }).id);
  await pool.query("UPDATE pairing_requests SET status='declined' WHERE id=$1 AND owner_id=$2 AND status='pending'", [id, user.id]);
  return { cancelled: true };
});

const wss = new WebSocketServer({ noServer: true, maxPayload: 700_000, perMessageDeflate: false });
app.server.on('upgrade', (req, socket, head) => {
  socket.on('error', () => socket.destroy());
  if (req.url !== '/v1/tv/socket') { socket.destroy(); return; }
  upgrades++;
  void tv(req.headers.authorization).then(async device => {
    if ((await incrementRate(pool, `socket:${device.id}`, 60000)).current > 20) throw fail(429, 'Too many TV reconnects.');
    await broker.listen();
    wss.handleUpgrade(req, socket, head, ws => {
      const entry: Connection = { ws, id: randomUUID(), syncing: false, again: false };
      sockets.get(device.id)?.ws.close(4000, 'Reconnected');
      sockets.set(device.id, entry);
      let alive = true;
      const heartbeat = setInterval(() => {
        if (!alive) return ws.terminate();
        alive = false; ws.ping();
        void broker.renew(device.id, entry.id).then(ok => { if (!ok) void syncDevice(device.id); }).catch(() => ws.close(1011, 'Relay unavailable'));
      }, 15000);
      ws.on('pong', () => { alive = true; });
      ws.on('error', () => ws.terminate());
      ws.on('close', () => {
        clearInterval(heartbeat);
        if (sockets.get(device.id) === entry) sockets.delete(device.id);
        void broker.release(device.id, entry.id).catch(() => {});
        if (!sockets.size && !upgrades) void broker.stopListening();
      });
      void broker.register(device.id, entry.id).then(() => {
        if (ws.readyState !== WebSocket.OPEN) return broker.release(device.id, entry.id);
        return syncDevice(device.id);
      }).catch(() => ws.close(1011, 'Cannot register TV connection'));
      let frameWindow = Date.now(), frames = 0;
      ws.on('message', raw => { void (async () => {
        if (Date.now() - frameWindow >= 1000) { frameWindow = Date.now(); frames = 0; }
        if (++frames > 30) { ws.close(1008, 'Too many TV messages'); return; }
        if (sockets.get(device.id) !== entry) return;
        const message = z.object({ type: z.enum(['result', 'pairDecision']), requestId: uuid, ok: z.boolean().optional(), state: tvState.optional(), error: z.string().max(300).optional(), approved: z.boolean().optional() }).strict().parse(JSON.parse(raw.toString()));
        if (message.type === 'result') {
          await broker.complete(device.id, entry.id, message.requestId,
            message.ok === true && message.state ? { ok: true, state: message.state } : { ok: false, error: message.error || 'TV could not apply this command.' });
          return;
        }
        if (typeof message.approved !== 'boolean') return;
        const client = await pool.connect();
        try {
          await client.query('BEGIN');
          const current = await client.query('SELECT owner_id FROM devices WHERE id=$1 FOR UPDATE', [device.id]);
          const pair = await client.query("SELECT owner_id FROM pairing_requests WHERE id=$1 AND device_id=$2 AND status='pending' AND expires_at>now() FOR UPDATE", [message.requestId, device.id]);
          if (!pair.rows[0] || !current.rows[0] || current.rows[0].owner_id) { await client.query('ROLLBACK'); return; }
          if (message.approved) {
            await client.query('UPDATE devices SET owner_id=$1 WHERE id=$2', [pair.rows[0].owner_id, device.id]);
            await client.query('DELETE FROM pairing_codes WHERE device_id=$1', [device.id]);
          }
          await client.query('UPDATE pairing_requests SET status=$1 WHERE id=$2', [message.approved ? 'approved' : 'declined', message.requestId]);
          await client.query('COMMIT');
          ws.send(JSON.stringify({ type: 'pairComplete', approved: message.approved }));
        } catch (error) { await client.query('ROLLBACK'); throw error; }
        finally { client.release(); }
      })().catch(error => { app.log.warn({ message: error instanceof z.ZodError ? 'Invalid TV message' : 'TV operation failed' }); }); });
    });
  }).catch(error => {
    const status = (error as { statusCode?: number }).statusCode;
    const code = status === 401 || status === 429 ? status : 503;
    const reason = code === 401 ? 'Unauthorized' : code === 429 ? 'Too Many Requests' : 'Service Unavailable';
    if (!socket.destroyed) socket.end(`HTTP/1.1 ${code} ${reason}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n`);
  }).finally(() => { upgrades--; if (!upgrades && !sockets.size) void broker.stopListening(); });
});

app.addHook('onClose', async () => {
  for (const connection of sockets.values()) connection.ws.terminate();
  wss.close(); await broker.stopListening(); await pool.end();
});
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { void app.close(); });
await app.ready();
if (process.env.VERCEL !== '1') await app.listen({ host: env.HOST, port: env.PORT });
export default app.server;
