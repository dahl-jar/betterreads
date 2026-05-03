# Cloudflare Tunnel and Access for the actuator endpoints

The Spring Boot management port is bound to `127.0.0.1:8081` on the Hetzner VM. That's correct for security but useless if you can't read the metrics from your laptop. This runbook covers the setup that gets `actuator/health`, `actuator/health/liveness`, `actuator/health/readiness`, and `actuator/prometheus` reachable from a browser, but only after Cloudflare Access authenticates the request.

This setup is implemented. The runbook reads as a recipe for re-doing it on a fresh VM, debugging a broken tunnel, or porting the pattern to another project.

## Prerequisites

A domain on Cloudflare DNS. Currently `betterreadsapp.com` via Cloudflare Registrar.

The VM provisioned and reachable over SSH, with `cloudflared` installed. On the current Hetzner CX23 the install was `apt install -y cloudflared` from Cloudflare's APT repo (the `.deb` for `linux-amd64`).

The Spring Boot app listening on the VM with `management.server.port=8081` bound to `127.0.0.1`. Confirm with `curl http://127.0.0.1:8081/actuator/health` from inside an SSH session before doing anything Cloudflare-side.

## Architecture

The browser hits `https://metrics.betterreadsapp.com/actuator/prometheus`. Cloudflare's edge intercepts that hostname because the DNS record points at Cloudflare. Before forwarding the request, Cloudflare Access requires a Google SSO login from the configured email. After auth, Cloudflare attaches a `Cf-Access-Jwt-Assertion` header and forwards through the tunnel to the VM. The tunnel hands the request to `127.0.0.1:8081`. Spring Boot Actuator answers.

The tunnel runs as a systemd service on the VM and connects outbound to Cloudflare's edge over QUIC. There are no inbound firewall holes; the management port is bound to `127.0.0.1` only, which is the point.

## Domain setup, one time

Register the domain through Cloudflare Registrar or transfer in. Pick something like `nanthawatdahl.com` for personal use. After the domain is on Cloudflare, add an A record for the apex pointing wherever the public site lives (this can wait if the frontend isn't deployed yet).

Don't add a DNS record for `metrics.<domain>` manually. The tunnel creates that record itself when you run the `cloudflared tunnel route dns` command later.

## Tunnel setup on the VM

Two paths exist for connecting `cloudflared` to a Cloudflare account. The current setup uses the **dashboard-token** path, which is simpler. The original interactive `tunnel login` / `tunnel create` flow still works and is documented below as an alternative.

### Dashboard-token path (current setup)

In the Cloudflare Zero Trust dashboard, create a tunnel and copy the connector token (a base64 string) shown after creation. Save it in 1Password. On the VM:

```bash
apt install -y cloudflared
cloudflared service install <token>
```

The install registers a systemd unit at `/etc/systemd/system/cloudflared.service`, starts the tunnel, and connects to Cloudflare's edge over QUIC. The tunnel's ingress rules are configured in the dashboard ("Public hostname" tab on the tunnel detail page) rather than in a local config file. This flow is what the current production VM uses.

When you run `cloudflared service install <token>` the token ends up on the systemd command line, visible to anyone with shell on the VM via `ps` or `systemctl status`. On a single-user VM that's acceptable. For shared hosts, prefer the credentials-file path below.

### Interactive credentials-file path (alternative)

Install `cloudflared` from Cloudflare's APT repo (matches the architecture of the host: `linux-amd64` on x86, `linux-arm64` on ARM):

```bash
curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared.deb
rm cloudflared.deb
```

Authenticate the daemon against your Cloudflare account. This opens a browser locally for OAuth; on a headless VM you copy the URL out and complete the flow on your laptop:

```bash
cloudflared tunnel login
```

The login writes a cert to `~/.cloudflared/cert.pem`. That cert is how `cloudflared` proves it's authorized to manage tunnels for your account. Treat it like an SSH key.

Create the tunnel:

```bash
cloudflared tunnel create betterreads
```

Note the tunnel ID it prints. The tunnel credentials get written to `~/.cloudflared/<tunnel-id>.json` on the VM. Don't commit that file or copy it off the VM.

Drop a config file at `/etc/cloudflared/config.yml` based on the template at `infra/cloudflare/tunnel-config.yml.example` in this repo. Fill in the tunnel ID and the credentials path. The ingress rules route `metrics.betterreadsapp.com` to the management port and let everything else fall through to a 404.

Route DNS at the metrics hostname:

```bash
cloudflared tunnel route dns betterreads metrics.betterreadsapp.com
```

This creates a CNAME record in Cloudflare DNS that points at the tunnel. Cloudflare resolves the hostname to its edge, the edge knows the tunnel ID, the tunnel forwards into your VM. The DNS record appears in the Cloudflare dashboard under DNS as a CNAME with a target like `<tunnel-id>.cfargotunnel.com`.

Install the tunnel as a systemd service so it survives reboots:

```bash
sudo cloudflared service install
sudo systemctl enable --now cloudflared
sudo systemctl status cloudflared
```

Test from your laptop:

```bash
curl https://metrics.betterreadsapp.com/actuator/health
```

You should get a Cloudflare login redirect, not the actuator response. That's the correct intermediate state. The next section adds the Access policy that defines who the tunnel will let through.

## Access policy

Cloudflare Zero Trust is free for up to 50 users. Set up the team domain once at the Zero Trust dashboard at one.dash.cloudflare.com. The team domain is something like `nanthawatdahl.cloudflareaccess.com`; you pick the prefix.

Create an Access application targeting the metrics hostname. The shape of the application configuration lives at `infra/cloudflare/access-policy.json.example` in this repo, in the format the Cloudflare API uses. The key parts: the application type is `self_hosted`, the hostname is `metrics.betterreadsapp.com`, the session duration is something short like 24 hours, and the policy includes only your email.

You can create it through the dashboard (Access -> Applications -> Add) or through the API with a token. Dashboard is faster the first time. Read off the Application Audience (AUD) tag once it's created; you'll need that AUD in the Spring config to validate the JWT server-side.

Test again:

```bash
curl https://metrics.betterreadsapp.com/actuator/health
```

The first call returns a redirect to Google login. After signing in with your configured email, the actuator response comes back. Cloudflare also drops a session cookie in your browser, so subsequent calls within the session window skip the login.

## Service tokens for non-browser scraping

A Prometheus instance scraping `actuator/prometheus` can't do interactive Google login. Cloudflare Access supports service tokens for this exact case: a long-lived `CF-Access-Client-Id` and `CF-Access-Client-Secret` pair that authenticate non-human callers.

Create a service token in the Zero Trust dashboard under Access -> Service Auth -> Service Tokens. Give it a name like `prometheus-scraper`. Cloudflare shows the secret exactly once; store both halves in 1Password.

Add a service-token policy to the metrics application. The policy has type `non_identity` (service tokens don't have a user identity attached) and matches the named token. Without this policy, the token authenticates against Cloudflare but Access still rejects it because the application's user-policy doesn't list it.

Test the service token:

```bash
curl -H "CF-Access-Client-Id: <id>.access" \
     -H "CF-Access-Client-Secret: <secret>" \
     https://metrics.betterreadsapp.com/actuator/health
```

This returns the actuator response without a redirect. That's the path a real Prometheus scrape would take.

## Hardening: validate the Cloudflare JWT in Spring

The architecture above is correct on day one but has a layering problem: anyone who reaches the tunnel directly bypasses Cloudflare Access. Direct tunnel access shouldn't be possible (the tunnel only accepts connections from Cloudflare's edge), but defense in depth says the application should also validate the `Cf-Access-Jwt-Assertion` header that Cloudflare attaches.

The TODO marker in `SecurityConfig.managementSecurityFilterChain` references this. The work is straightforward but real:

- Add a dependency on `nimbus-jose-jwt` or use Spring Security's resource server mode.
- Build a filter (or `@Bean OncePerRequestFilter`) that runs only on the management chain. It reads `Cf-Access-Jwt-Assertion`, fetches Cloudflare's JWKS from `https://<team-domain>/cdn-cgi/access/certs`, validates the signature, checks the `aud` claim against the application AUD, and rejects the request if any check fails.
- Cache the JWKS for an hour so the validation isn't a network round-trip per request.

Add the AUD as `CLOUDFLARE_ACCESS_AUD` in `/etc/default/betterreads-app` (or wherever the app picks up env on the VM), and the team domain as `CLOUDFLARE_TEAM_DOMAIN`. The application reads both at startup. Failure to validate the JWT becomes a 401 from the actuator chain.

Until that filter is wired, the management chain is `permitAll`. That's safe under the assumption that the tunnel is the only path to port 8081, which it is by OS-level binding (`management.server.address=127.0.0.1`). The JWT validation is belt-and-braces, not a primary defense. Add it the next time you're touching the auth code anyway.

## Operational notes

The tunnel auto-reconnects if it loses Cloudflare's edge. `systemctl status cloudflared` shows the current state. Logs are at `journalctl -u cloudflared -f`.

The Access session cookie is HttpOnly and Secure, scoped to the team domain. Logging out of Cloudflare in another tab kills it everywhere; that's normal.

If the tunnel goes down, the metrics URL returns a Cloudflare error page (525 or 1033, depending on what failed). The actual API on `https://api.betterreadsapp.com` keeps working because it's a different tunnel ingress rule (or a different tunnel entirely). The two are independent.

Cloudflare Access logs every authentication decision. Find them in Zero Trust -> Logs -> Access. Useful for noticing if someone tries to hit the metrics hostname from an IP you don't recognize.

## What this runbook does not cover

The setup of the API tunnel that serves the actual app traffic. That's a separate ingress rule on the same `cloudflared` daemon, configured in the same `config.yml`, but routes a different hostname to port 8080 instead of 8081. Out of scope here because it's not specific to the actuator.

Cloudflare WAF rules and bot-management. Free tier covers basic DDoS mitigation; per-endpoint rate limiting requires a paid plan, which the project deliberately stays off. The in-app Bucket4j rate limiter handles the auth endpoints; everything else falls under whatever Cloudflare's free DDoS protection catches.

Multi-region failover. The Hetzner setup is one VM in one Helsinki datacenter. If the host is down, metrics are unreachable. Acceptable cost for the project's scale.
