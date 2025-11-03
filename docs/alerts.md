# Alerts (Model & Flow)

Goal: warn when CPU, RAM, or Disk usage is high for an asset.

Concepts
- Thresholds (configurable at runtime): CPU high %, RAM high %, Disk high %.
- Alert record: { id, ts, hostname, type, message, acknowledged } stored in DB.

Flow
1) Metrics are ingested and evaluated against thresholds.
2) When a crossing occurs, an alert is created.
3) UI shows alerts list; user can filter and acknowledge.
4) Acknowledged alerts remain for history; they are excluded by default in the “unacknowledged” filter.

APIs
- GET `/api/alerts?host&ack&limit&offset` ? list
- POST `/api/alerts/{id}/ack` ? marks as acknowledged
- GET `/api/admin/alerts/config` / PUT to update thresholds

UI
- Alerts page shows thresholds (editable) and a filtered table.
- Badges on the Assets list show the count of unacknowledged alerts per host.

Notes
- Threshold tuning depends on your workloads; start high (e.g., CPU 90%) and reduce once you trust signal quality.
- Disk usage per filesystem is exposed in the Asset detail “Disks” card (for VMs with QGA).
