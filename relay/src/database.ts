import { isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

export function databaseConnectionString(value: string) {
  const url = new URL(value);
  const certificate = url.searchParams.get('sslrootcert');
  if (certificate && !isAbsolute(certificate)) {
    url.searchParams.set('sslrootcert', fileURLToPath(new URL(`../${certificate}`, import.meta.url)));
  }
  return url.toString();
}
