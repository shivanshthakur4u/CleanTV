import { isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

export function databaseConfig(value: string, ca: string) {
  const url = new URL(value);
  if (url.searchParams.get('sslmode') !== 'verify-full' || !url.searchParams.get('sslrootcert')) {
    throw new Error('DATABASE_URL must require full TLS verification and a root certificate.');
  }
  for (const name of ['ssl', 'sslmode', 'sslrootcert', 'sslcert', 'sslkey', 'sslpassword', 'uselibpqcompat']) url.searchParams.delete(name);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true as const } };
}

export function databaseConnectionString(value: string) {
  const url = new URL(value);
  const certificate = url.searchParams.get('sslrootcert');
  if (certificate && !isAbsolute(certificate)) {
    url.searchParams.set('sslrootcert', fileURLToPath(new URL(`../${certificate}`, import.meta.url)));
  }
  return url.toString();
}
