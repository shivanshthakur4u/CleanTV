# Supabase database certificate

`prod-ca-2021.crt` is Supabase's public root certificate, not a private key.
The download URL matches the production certificate template in [Supabase Studio's source](https://github.com/supabase/supabase/blob/master/apps/studio/hooks/custom-content/custom-content.json).
The certificate was downloaded over verified HTTPS from [Supabase's distribution bucket](https://supabase-downloads.s3-ap-southeast-1.amazonaws.com/prod/ssl/prod-ca-2021.crt).

- Subject: Supabase Root 2021 CA.
- DER SHA-256 fingerprint: `807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa`.
- Expires: 26 April 2031.

For Supabase connections, include `sslmode=verify-full&sslrootcert=certs/prod-ca-2021.crt` in the database URI query.
Relative root-certificate paths resolve from the relay application directory, independently of the process working directory.
The certificate is also included in the Docker image.
This scopes trust to the database connection without changing the operating system's trust store.
If Supabase rotates its root certificate, obtain the replacement from its official dashboard or published distribution and verify it before replacing this file.

## TL;DR

The bundled public certificate enables database certificate and hostname verification.
Keep it current when Supabase changes its certificate chain.
