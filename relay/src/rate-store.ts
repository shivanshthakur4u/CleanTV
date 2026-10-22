import { createHash } from 'node:crypto';
import type { FastifyRateLimitOptions } from '@fastify/rate-limit';
import type pg from 'pg';

export async function incrementRate(pool: pg.Pool, key: string, window: number) {
  const hash = createHash('sha256').update(key).digest('hex');
  const result = await pool.query<{ hits: number; ttl: string }>("INSERT INTO relay_rate_limits(key,hits,expires_at) VALUES($1,1,now()+$2*interval '1 millisecond') ON CONFLICT(key) DO UPDATE SET hits=CASE WHEN relay_rate_limits.expires_at<=now() THEN 1 ELSE relay_rate_limits.hits+1 END, expires_at=CASE WHEN relay_rate_limits.expires_at<=now() THEN EXCLUDED.expires_at ELSE relay_rate_limits.expires_at END RETURNING hits,GREATEST(0,EXTRACT(EPOCH FROM expires_at-now())*1000)::text AS ttl", [hash, window]);
  return { current: result.rows[0]!.hits, ttl: Math.ceil(Number(result.rows[0]!.ttl)) };
}
export function databaseRateStore(pool: pg.Pool) {
  type Options = FastifyRateLimitOptions & { routeInfo?: { method?: unknown; url?: string } };
  return class DatabaseRateStore {
    constructor(private options: Options) {}
    incr(key: string, callback: (error: Error | null, result?: { current: number; ttl: number }) => void, window = 60000) {
      const route = this.options.routeInfo;
      const scope = route?.url ? `${String(route.method)}:${route.url}` : 'global';
      void incrementRate(pool, `${scope}:${key}`, window).then(result => callback(null, result), error => callback(error));
    }
    child(options: Options) { return new DatabaseRateStore(options); }
  };
}
