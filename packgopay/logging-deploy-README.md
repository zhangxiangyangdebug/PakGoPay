# Logging Deploy (Single Script)

Unified script:

- `scripts/deploy_unified.sh`

This script now contains backend deploy + log server deploy + promtail deploy in one file.
Default log server host is fixed to `121.5.179.75` (can still be overridden by `--log-server-host`).
Default backend host is `8.219.132.103`.
In `--mode all`, if `--business-host` is not provided, it defaults to backend host.

## 1) Backend only

```bash
bash logging-deploy/scripts/deploy_unified.sh \
  --mode backend
```

## 2) Log server only (standalone host)

```bash
GRAFANA_ADMIN_PASSWORD='YourStrongPassword!' \
bash logging-deploy/scripts/deploy_unified.sh \
  --mode log-server
```

Default ports:
- Loki: `3100`
- Grafana: `3000`

## 3) Promtail only (business host -> standalone log server)

```bash
bash logging-deploy/scripts/deploy_unified.sh \
  --mode promtail \
  --business-host 8.219.132.103
```

## 4) All in one (backend + log server + promtail)

```bash
bash logging-deploy/scripts/deploy_unified.sh --mode all
```

Notes:
- `--mode all` order: `backend -> log-server -> promtail`.
- `--log-server-host` is used to deploy Loki/Grafana on a dedicated machine and auto-generate promtail push URL.
- You can still override push URL directly: `--log-server-push-url http://x.x.x.x:3100/loki/api/v1/push`.
- Promtail app log collection is fixed to `${APP_LOG_DIR}/root*.log` (only root-prefix logs).
- Promtail parses root log format and extracts fields (`log_time/pid/tid/level/traceId/class/msg`), and promotes `level` and `traceId` as Loki labels.
- Log-server deploy checks Docker first. If Docker is missing, it installs Docker automatically.
- Log-server deploy auto-fixes permissions for `loki-data`/`grafana-data` on first deploy.
- Log-server deploy includes `grafana-image-renderer` for `/render/*` image APIs.
- Redeploy is skipped only when `loki + grafana + grafana-renderer` are all healthy.
- Re-deploy will recreate containers but keeps bind-mounted data (`loki-data`, `grafana-data`), so existing dashboards/data-sources are preserved.

## 5) Grafana search examples

In Grafana Explore (Loki datasource):

- `{job="app", app="pakgopay", env="prod"}`
- `{job="nginx", env="prod"}`
- `{job="app"} |= "ERROR"`
