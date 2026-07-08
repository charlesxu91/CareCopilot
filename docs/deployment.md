# CareCopilot Deployment

CareCopilot is a Spring Boot backend for Chinese medical-assistant workflows. In production mode it stores cases, timelines, audit events, and trainable dataset metadata in PostgreSQL and submits agent work to AgentPlane.

## Runtime Contracts

- `POST /cases` creates a CareCopilot case and an AgentPlane session.
- `POST /workflows/symptom-intake` creates artifacts, runs, task jobs, timeline entries, and audit events.
- `GET /cases/{caseId}/audit-events` returns traceable agent execution events.
- `POST /datasets/export/jsonl` exports trainable audit records as JSONL.
- `/actuator/health/readiness`, `/actuator/health/liveness`, and `/actuator/prometheus` support operations.

## Docker Compose

From the `carecopilot` directory:

```bash
docker compose -f deploy/docker-compose.prod.yml up --build
```

Set `CARECOPILOT_AGENTPLANE_BASE_URL` when AgentPlane is not available as `http://agentplane:8080`.

## Kubernetes

```bash
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl apply -f deploy/kubernetes/
```

The default manifests expect AgentPlane at `agentplane-control-plane.agentplane.svc.cluster.local:8080`.

## Helm

```bash
helm upgrade --install carecopilot deploy/helm/carecopilot --namespace carecopilot --create-namespace
```

Override AgentPlane and image settings with:

```bash
helm upgrade --install carecopilot deploy/helm/carecopilot \
  --namespace carecopilot \
  --create-namespace \
  --set image.repository=your-registry/carecopilot-backend \
  --set image.tag=0.1.0 \
  --set agentplane.baseUrl=http://agentplane-control-plane.agentplane.svc.cluster.local:8080
```

## Dataset Export

```bash
curl -X POST http://127.0.0.1:18081/datasets/export/jsonl \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"tenant-carecopilot-dev","allowedTrainability":["TRAINABLE_REDACTED_TEXT","TRAINABLE_TELEMETRY"]}'
```
