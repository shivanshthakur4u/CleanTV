import { readFile } from 'node:fs/promises';
import pg from 'pg';
import { databaseConnectionString } from './database.js';

if (!process.env.DATABASE_URL) throw new Error('Set DATABASE_URL before running migrations.');
const pool = new pg.Pool({ connectionString: databaseConnectionString(process.env.DATABASE_URL) });
const client = await pool.connect();
try {
  await client.query("SELECT pg_advisory_lock(hashtext('tv-home-migrations'))");
  let changed = false;
  for (const name of ['001_devices.sql', '002_relay_routing.sql']) {
    const table = await client.query("SELECT to_regclass('public.schema_migrations') AS name");
    const applied = table.rows[0].name ? await client.query('SELECT 1 FROM schema_migrations WHERE name=$1', [name]) : { rowCount: 0 };
    if (!applied.rowCount) {
      await client.query(await readFile(new URL(`../migrations/${name}`, import.meta.url), 'utf8'));
      console.info(`Applied ${name}`); changed = true;
    }
  }
  if (!changed) console.info('Database is up to date.');
} finally {
  await client.query("SELECT pg_advisory_unlock(hashtext('tv-home-migrations'))");
  client.release();
  await pool.end();
}
