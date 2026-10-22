# TV Home relay protocol, version 1

All production HTTP calls use HTTPS, and the TV socket uses WSS.
The phone sends its Supabase access token using `Authorization: Bearer <token>`.
The TV sends its random device secret using `Authorization: Device <token>`.
Tokens are never sent in query strings.

## Pairing

1. `POST /v1/tv/register` creates an unclaimed TV and returns its ID and random secret.
2. The TV stores its secret encrypted using a key held in Android KeyStore.
3. The TV connects to `/v1/tv/socket` and requests `POST /v1/tv/pairing`.
4. The server returns a random ten-character code valid for five minutes.
5. An authenticated phone submits that code to `POST /v1/pair`.
6. The TV receives `pairRequest` with a request ID and the account email.
7. The TV user explicitly accepts or declines, sending `pairDecision`.
8. The server atomically assigns ownership and invalidates the code after approval.
9. The phone polls `GET /v1/pair/:requestId` for the result.

The code alone never grants ownership without confirmation on the TV.
Pairing requests expire after two minutes.
The phone can cancel a pending request with `DELETE /v1/pair/:requestId`.
Removing a TV with `DELETE /v1/devices/:id` deletes its authorization record and closes its live socket.

## Device operations

`GET /v1/devices` returns only the account's devices and their current connection status.
`GET /v1/devices/:id/state` asks the TV for its current state.
`POST /v1/devices/:id/command` sends one validated command to an owned, online TV.

| Command | Payload |
| --- | --- |
| `getState` | No additional fields. |
| `settings` | A partial settings object containing scene, dim, roomName, favorites, or clock24h. |
| `wallpaper` | A base64 JPEG, PNG, or WebP image, capped before and after decoding. |
| `launchApp` | The package identifier of an installed app. |
| `navigate` | up, down, left, right, select, back, or home. |

The relay wraps each command in a random request ID and a deadline.
The TV rejects expired commands and validates settings and images again locally.
The relay stores pending commands in server-only PostgreSQL tables, bound to one live connection generation and a fourteen-second deadline.
Commands are claimed once and are not replayed after reconnection or expiry.
Replies are stored until the originating request reads them, allowing HTTP and WebSocket connections to use different Vercel instances.
Expired payloads are removed by request-triggered cleanup.
An acknowledged result contains the TV's current state, including its installed app labels and current appearance.
A timeout means the change was not confirmed; the phone should refresh before retrying.

## Hosting and connection lifecycle

`relay_connections` holds forty-second connection leases renewed by the socket heartbeat.
PostgreSQL notifications wake the instance holding the relevant TV socket; payloads are read from the protected command table rather than placed in notification messages.
Revocation wakes the socket-owning instance even when another instance handles the HTTP request.
All HTTP rate counters are shared in PostgreSQL, and authenticated TV reconnects have a shared per-device limit.
The database must use the session pooler or a direct connection because the socket listener uses PostgreSQL LISTEN.
The TV reconnects with backoff when Vercel ends a WebSocket invocation.
The phone and TV relay origins are supplied at build time, never by ordinary users.

## TL;DR

Account ownership, expiring pairing, TV approval, bounded commands, and explicit acknowledgments govern all phone control.
