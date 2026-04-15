# Logging Deploy (Single Script)

Unified script:

- `scripts/deploy_unified.sh`

This script now contains backend deploy + log server deploy + promtail deploy in one file.
Default log server host is fixed to `121.5.179.75` (can still be overridden by `--log-server-host`).
Default backend host is `47.237.209.29`.
In `--mode promtail` or `--mode all`, if `--business-host` is not provided, it defaults to backend host.

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
bash logging-deploy/scripts/deploy_unified.sh --mode promtail
```

Optional override business host:

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
- In `--mode promtail`, `--business-host` is optional and defaults to backend host.
- `--log-server-host` is used to deploy Loki/Grafana on a dedicated machine and auto-generate promtail push URL.
- You can still override push URL directly: `--log-server-push-url http://x.x.x.x:3100/loki/api/v1/push`.
- Promtail app log collection is fixed to `${APP_LOG_DIR}/root*.log` (only root-prefix logs).
- App file logs (`root/timer/dmq`) are written as single-line JSON.
- Promtail parses JSON fields such as `ts/level/traceId/logger/message/exception`, uses `ts` as Loki event timestamp, and promotes only low-cardinality labels such as `level` to Loki labels.
- Loki query defaults were raised for dashboard stability: higher `max_outstanding_per_tenant`, higher `max_concurrent`, and smaller `split_queries_by_interval` to reduce `too many outstanding requests` on multi-panel boards.
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

## 6) PakGoPay Alert Rules (LogQL)

- Template file (in repo): `docs/pakgopay-grafana-logql-alerts.yaml`
- Template file (deploy workspace): `logging-deploy/templates/pakgopay-grafana-logql-alerts.yaml`
- Recommended 6 categories:
  - `infra_mq_exception`
  - `infra_redis_snowflake_exception`
  - `biz_order_timeout_abnormal`
  - `biz_order_create_failed`
  - `security_jwt_abnormal`
  - `perf_db_slow_query`

Recommended labels for each alert rule:

- `service`
- `env`
- `severity`
- `category`
- `errorCode` (when exists)
- `bizType` (when exists)
- `api` (when exists)
- `reason` (when exists)
- `mapper` (when exists)
- `threshold` (when exists)
- `rootException` (when exists)
- `logger` (when exists)

These labels are consumed by backend webhook classification, and support Telegram quick mute for the same alert class.
