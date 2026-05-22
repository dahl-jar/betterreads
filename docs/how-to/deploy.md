# How to deploy

Production runs on a single-node k3s cluster, deployed by Argo CD from a Git manifests repo. This runbook covers shipping a new version, rolling back, changing config, and restarting workloads.

`kubectl` reaches the cluster API over the private network. Confirm access first:

```bash
kubectl get nodes
```

## Shipping a new version

Deploys are automatic. On push to `main`, CI runs the quality gate, builds the image, and pushes it to GHCR tagged `latest` and `sha-<commit>`. Argo CD picks up the new image and rolls the Deployment.

To watch a rollout:

```bash
kubectl rollout status deploy/betterreads -n betterreads
curl https://api.betterreadsapp.com/healthz
```

If the new pod doesn't go ready, check its logs:

```bash
kubectl logs -n betterreads deploy/betterreads --tail=50
```

## Rolling back

Argo CD keeps deployment history. Roll the workload back to the previous ReplicaSet:

```bash
kubectl rollout undo deploy/betterreads -n betterreads
```

To pin to a known-good image instead, set the Deployment to a specific `sha-<commit>` tag in the manifests repo and let Argo sync. Reverting the manifests commit has the same effect, and keeps Git as the source of truth.

## Changing config

Non-secret config lives in the `betterreads-config` ConfigMap in the manifests repo. Edit it there, commit, and Argo applies it. The app picks up the change on its next restart:

```bash
kubectl rollout restart deploy/betterreads -n betterreads
```

Secrets are sealed in the manifests repo and decrypted in-cluster. Reseal the changed value, commit, then restart the Deployment.

### Postgres credentials persist in the volume

Postgres only reads `POSTGRES_USER` / `POSTGRES_PASSWORD` on first init of the PVC. After that the credentials live in the data volume and the env is ignored. If the password in the secret drifts from what Postgres was initialized with, the app fails to connect with `FATAL: password authentication failed`.

To rotate, `ALTER USER` inside Postgres first, then update the secret:

```bash
kubectl exec -it -n betterreads postgres-0 -- psql -U betterreads -d postgres
postgres=# ALTER USER betterreads WITH PASSWORD '<new>';
postgres=# ALTER USER betterreads_app WITH PASSWORD '<new app>';
postgres=# \q
```

Then reseal the secret with the new value and restart the app. Don't change the secret first; the running database keeps the old password until `ALTER USER` runs.

## Restarting workloads

```bash
kubectl rollout restart deploy/betterreads -n betterreads     # app
kubectl rollout restart statefulset/redis -n betterreads      # redis
kubectl rollout restart deploy/cloudflared -n cloudflared     # tunnel
```

Restarting Postgres (`statefulset/postgres`) drops connections briefly; the app's pool reconnects.

## Logs and status

```bash
kubectl get pods -A                                    # everything
kubectl logs -n betterreads deploy/betterreads -f      # app, follow
kubectl logs -n betterreads postgres-0 -f              # Postgres
kubectl logs -n cloudflared deploy/cloudflared -f      # tunnel
```

Argo CD's UI shows sync state and the resource tree per app.

## If the tunnel is down

The API returns a Cloudflare error page (530/1033) when no connector is registered. Check the cloudflared pods:

```bash
kubectl get pods -n cloudflared
kubectl logs -n cloudflared deploy/cloudflared --tail=30
```

Two replicas run, so a single pod restart doesn't drop the tunnel.
