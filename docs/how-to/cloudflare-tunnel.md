# How to set up the Cloudflare Tunnel for actuator endpoints

The Spring Boot management port is bound to `127.0.0.1:8081`. This runbook covers the setup that makes `actuator/health`, `actuator/health/liveness`, `actuator/health/readiness`, and `actuator/prometheus` reachable from a browser, but only after Cloudflare Access authenticates the request. For the request flow and rationale, see [explanation/deployment-and-frontend.md](../explanation/deployment-and-frontend.md).

## Prerequisites

- A domain on Cloudflare DNS.
- A VM provisioned and reachable over SSH, with `cloudflared` installed (`apt install -y cloudflared` from Cloudflare's APT repo).
- Spring Boot listening on the VM with `management.server.port=8081` bound to `127.0.0.1`. Confirm with `curl http://127.0.0.1:8081/actuator/health` over SSH before doing anything Cloudflare-side.

## Tunnel setup on the VM

The current setup uses the **dashboard-token** path. The interactive `tunnel login` / `tunnel create` flow is documented below as an alternative for shared hosts.

### Dashboard-token path (current setup)

In the Cloudflare Zero Trust dashboard, create a tunnel and copy the connector token shown after creation. Save it; Cloudflare shows it once. On the VM:

```bash
apt install -y cloudflared
cloudflared service install <token>
```

The install registers a systemd unit at `/etc/systemd/system/cloudflared.service`, starts the tunnel, and connects to Cloudflare's edge. Ingress rules are configured in the dashboard ("Public hostname" tab on the tunnel detail page).

The token ends up on the systemd command line, visible to anyone with shell on the VM via `ps` or `systemctl status`. On a multi-user VM, prefer the credentials-file path.

### Interactive credentials-file path (alternative)

Install `cloudflared` from Cloudflare's APT repo (architecture must match the host: `linux-amd64` on x86, `linux-arm64` on ARM):

```bash
curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared.deb
rm cloudflared.deb
```

Authenticate the daemon. This opens a browser for OAuth; on a headless VM the URL must be copied out and the flow completed elsewhere:

```bash
cloudflared tunnel login
```

Login writes a cert to `~/.cloudflared/cert.pem`. Treat it like an SSH key.

Create the tunnel:

```bash
cloudflared tunnel create betterreads
```

The tunnel credentials get written to `~/.cloudflared/<tunnel-id>.json`. Don't commit that file.

Drop a config file at `/etc/cloudflared/config.yml` based on `infra/cloudflare/tunnel-config.yml.example`. Fill in the tunnel ID and credentials path.

Route DNS at the metrics hostname:

```bash
cloudflared tunnel route dns betterreads metrics.betterreadsapp.com
```

Install as a systemd service:

```bash
sudo cloudflared service install
sudo systemctl enable --now cloudflared
```

Test from a workstation:

```bash
curl https://metrics.betterreadsapp.com/actuator/health
```

A Cloudflare login redirect is the correct intermediate state. The next section adds the Access policy.

## Access policy

Set up the team domain once at one.dash.cloudflare.com. The team domain takes the form `<prefix>.cloudflareaccess.com`.

Create an Access application targeting the metrics hostname. The shape of the application config lives at `infra/cloudflare/access-policy.json.example`. Key parts: application type `self_hosted`, hostname `metrics.betterreadsapp.com`, short session duration (24 hours), policy includes only authorized email addresses.

Read off the Application Audience (AUD) tag once the app is created; it's needed in the Spring config.

After signing in with a configured email, the actuator response comes back. Cloudflare drops a session cookie scoped to the team domain.

## Service tokens for non-browser scraping

A Prometheus instance scraping `actuator/prometheus` can't do interactive Google login. Cloudflare Access supports service tokens: a long-lived `CF-Access-Client-Id` and `CF-Access-Client-Secret` pair.

Create a service token in the Zero Trust dashboard under Access, Service Auth, Service Tokens. Cloudflare shows the secret once.

Add a service-token policy to the metrics application. Type `non_identity`, matched on the named token. Without this policy, Access rejects the token even though it authenticates.

Test:

```bash
curl -H "CF-Access-Client-Id: <id>.access" \
     -H "CF-Access-Client-Secret: <secret>" \
     https://metrics.betterreadsapp.com/actuator/health
```

This returns the actuator response without a redirect.

## Hardening: Cloudflare JWT validation in Spring

The management chain in `SecurityConfig.managementSecurityFilterChain` validates the `Cf-Access-Jwt-Assertion` header. A request that bypasses the tunnel and reaches `localhost:8081` directly is rejected without a valid Cloudflare-signed JWT.

How it's wired:

- `spring-boot-starter-oauth2-resource-server` brings in the JWT validation infrastructure plus `nimbus-jose-jwt`.
- `CloudflareAccessConfig` creates a `JwtDecoder` bean only when both `cloudflare.access.aud` and `cloudflare.access.team-domain` are non-blank. The decoder fetches Cloudflare's JWKS from `https://<team-domain>/cdn-cgi/access/certs`, validates the signature, checks expiry via Spring's defaults, and runs `CloudflareAccessAudienceValidator`.
- `CloudflareAccessJwtAssertionResolver` reads the JWT from `Cf-Access-Jwt-Assertion` instead of `Authorization: Bearer`.
- The management chain uses `oauth2ResourceServer.jwt()` only when the decoder bean exists. When the env vars are blank (local dev), the chain stays at `permitAll`.

Required env on the VM:

```
CLOUDFLARE_ACCESS_AUD=<64-char hex AUD from the Access application>
CLOUDFLARE_TEAM_DOMAIN=<team prefix>.cloudflareaccess.com
```

Failure to validate the JWT becomes a 401 from the actuator chain.

## Operational notes

The tunnel auto-reconnects if it loses Cloudflare's edge. `systemctl status cloudflared` shows current state. Logs are at `journalctl -u cloudflared -f`.

If the tunnel goes down, the metrics URL returns a Cloudflare error page (525 or 1033). The API on `https://api.betterreadsapp.com` keeps working because it's a different ingress rule.

Cloudflare Access logs every authentication decision under Zero Trust, Logs, Access.

The API tunnel serving `api.betterreadsapp.com` uses the same `cloudflared` daemon with a different ingress rule routing to port 8080. Per-endpoint rate limiting lives in the backend via Bucket4j.
