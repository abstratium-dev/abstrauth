# Abstrauth Metrics Guide

This document describes the metrics exposed by Abstrauth and how to use them for monitoring and observability.

## Overview

Abstrauth uses [Micrometer](https://micrometer.io/) to collect and expose metrics in Prometheus format. Metrics are available at the `/q/metrics` endpoint on the management interface (default port: 9002).

## Accessing Metrics

### Prometheus Format (Default)
```bash
curl http://localhost:9002/q/metrics
```

### Plain Text Format
```bash
curl -H "Accept: text/plain" http://localhost:9002/q/metrics
```

## Metric Categories

### 1. Authentication Metrics

Track user authentication events and session management.

| Metric Name | Type | Description |
|------------|------|-------------|
| `abstrauth_auth_login_success_total` | Counter | Number of successful login attempts |
| `abstrauth_auth_login_failure_total` | Counter | Number of failed login attempts |
| `abstrauth_auth_signup_total` | Counter | Number of user signups |
| `abstrauth_auth_password_change_total` | Counter | Number of password changes |
| `abstrauth_sessions_active` | Gauge | Number of currently active sessions |

**Use Cases:**
- Monitor authentication success rate: `rate(abstrauth_auth_login_success_total[5m]) / rate(abstrauth_auth_login_total[5m])`
- Alert on unusual failed login attempts: `rate(abstrauth_auth_login_failure_total[5m]) > 10`
- Track active user sessions over time

### 2. OAuth Operation Metrics

Monitor OAuth 2.0 authorization and token flows.

| Metric Name | Type | Description |
|------------|------|-------------|
| `abstrauth_oauth_authorization_request_total` | Counter | Number of authorization requests |
| `abstrauth_oauth_authorization_approval_total` | Counter | Number of authorization approvals |
| `abstrauth_oauth_authorization_denial_total` | Counter | Number of authorization denials |
| `abstrauth_oauth_token_request_total` | Counter | Total number of token requests |
| `abstrauth_oauth_token_success_total` | Counter | Number of successful token requests |
| `abstrauth_oauth_token_failure_total` | Counter | Number of failed token requests |
| `abstrauth_oauth_token_revocation_total` | Counter | Number of token revocations |
| `abstrauth_oauth_token_introspection_total` | Counter | Number of token introspection requests |

**Use Cases:**
- Monitor token issuance success rate: `rate(abstrauth_oauth_token_success_total[5m]) / rate(abstrauth_oauth_token_request_total[5m])`
- Track authorization approval rate: `rate(abstrauth_oauth_authorization_approval_total[5m]) / rate(abstrauth_oauth_authorization_request_total[5m])`
- Alert on high token failure rates

### 3. Client Management Metrics

Track OAuth client and secret lifecycle events.

| Metric Name | Type | Description |
|------------|------|-------------|
| `abstrauth_client_creation_total` | Counter | Number of OAuth clients created |
| `abstrauth_client_deletion_total` | Counter | Number of OAuth clients deleted |
| `abstrauth_client_secret_creation_total` | Counter | Number of client secrets created |
| `abstrauth_client_secret_revocation_total` | Counter | Number of client secrets revoked |
| `abstrauth_client_secret_deletion_total` | Counter | Number of client secrets deleted |
| `abstrauth_clients_total` | Gauge | Total number of OAuth clients |

**Use Cases:**
- Monitor client growth: `abstrauth_clients_total`
- Track secret rotation frequency: `rate(abstrauth_client_secret_creation_total[1d])`
- Alert on unexpected client deletions

### 4. Role Management Metrics

Monitor role assignments for users and service accounts.

| Metric Name | Type | Description |
|------------|------|-------------|
| `abstrauth_role_assignment_total` | Counter | Number of role assignments to users |
| `abstrauth_role_removal_total` | Counter | Number of role removals from users |
| `abstrauth_service_role_assignment_total` | Counter | Number of role assignments to service accounts |
| `abstrauth_service_role_removal_total` | Counter | Number of role removals from service accounts |

**Use Cases:**
- Track role changes over time
- Monitor service account role assignments
- Alert on unusual role assignment patterns

### 5. Error Metrics

Track different types of errors in the system.

| Metric Name | Type | Description |
|------------|------|-------------|
| `abstrauth_error_authentication_total` | Counter | Number of authentication errors |
| `abstrauth_error_authorization_total` | Counter | Number of authorization errors |
| `abstrauth_error_validation_total` | Counter | Number of validation errors |

**Use Cases:**
- Monitor error rates by type
- Alert on error spikes
- Correlate errors with other metrics

### 6. System Metrics

General system and resource metrics.

| Metric Name | Type | Description |
|------------|------|-------------|
| `abstrauth_accounts_total` | Gauge | Total number of user accounts |
| `http_server_requests_seconds_count` | Counter | HTTP request count (auto-generated) |
| `http_server_requests_seconds_sum` | Counter | HTTP request duration sum (auto-generated) |
| `http_server_requests_seconds_max` | Gauge | Maximum HTTP request duration (auto-generated) |
| `jvm_*` | Various | JVM metrics (auto-generated) |
| `system_*` | Various | System metrics (auto-generated) |

**Use Cases:**
- Monitor user growth: `abstrauth_accounts_total`
- Track HTTP endpoint performance
- Monitor JVM memory and GC behavior
- Track system CPU and memory usage

## Grafana Dashboard Setup

### Prerequisites
- Prometheus server scraping Abstrauth metrics
- Grafana instance connected to Prometheus

### Prometheus Configuration

Add Abstrauth to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'abstrauth'
    static_configs:
      - targets: ['localhost:9002']
    metrics_path: '/q/metrics'
    scrape_interval: 15s
```

### Importing the Pre-built Dashboard

Abstrauth includes a ready-to-use Grafana dashboard that you can import directly:

1. **Download the dashboard file**: `docs/grafana-dashboard.json`

2. **Import into Grafana**:
   - Open Grafana web interface
   - Navigate to **Dashboards** → **Import**
   - Click **Upload JSON file**
   - Select `grafana-dashboard.json`
   - Select your Prometheus datasource
   - Click **Import**

3. **Dashboard includes**:
   - Authentication success rate
   - Active sessions gauge
   - Total accounts and clients
   - Token request rates and success rates
   - Error rates by type
   - HTTP request duration (p50, p95)
   - Authorization flow metrics
   - Management operations (client/secret/role changes)

The dashboard auto-refreshes every 10 seconds and shows the last hour of data by default.

### Sample Grafana Panels

#### 1. Authentication Success Rate
```promql
rate(abstrauth_auth_login_success_total[5m]) 
/ 
(rate(abstrauth_auth_login_success_total[5m]) + rate(abstrauth_auth_login_failure_total[5m]))
```

#### 2. Active Sessions
```promql
abstrauth_sessions_active
```

#### 3. Token Request Rate
```promql
rate(abstrauth_oauth_token_request_total[5m])
```

#### 4. Token Success Rate
```promql
rate(abstrauth_oauth_token_success_total[5m]) 
/ 
rate(abstrauth_oauth_token_request_total[5m])
```

#### 5. HTTP Request Duration (95th percentile)
```promql
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket[5m])
)
```

#### 6. Error Rate by Type
```promql
rate(abstrauth_error_authentication_total[5m])
rate(abstrauth_error_authorization_total[5m])
rate(abstrauth_error_validation_total[5m])
```

#### 7. Client and Account Growth
```promql
abstrauth_clients_total
abstrauth_accounts_total
```

### Recommended Alerts

#### High Failed Login Rate
```yaml
- alert: HighFailedLoginRate
  expr: rate(abstrauth_auth_login_failure_total[5m]) > 10
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High failed login rate detected"
    description: "Failed login rate is {{ $value }} per second"
```

#### Low Token Success Rate
```yaml
- alert: LowTokenSuccessRate
  expr: |
    rate(abstrauth_oauth_token_success_total[5m]) 
    / 
    rate(abstrauth_oauth_token_request_total[5m]) < 0.95
  for: 10m
  labels:
    severity: warning
  annotations:
    summary: "Token success rate below 95%"
    description: "Token success rate is {{ $value | humanizePercentage }}"
```

#### High Error Rate
```yaml
- alert: HighErrorRate
  expr: |
    rate(abstrauth_error_authentication_total[5m]) +
    rate(abstrauth_error_authorization_total[5m]) +
    rate(abstrauth_error_validation_total[5m]) > 5
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "High error rate detected"
    description: "Error rate is {{ $value }} per second"
```

## Configuration

### Enable/Disable Metrics

Metrics are enabled by default. To disable:

```properties
quarkus.micrometer.enabled=false
```

### Customize Metrics Endpoint

```properties
# Change management port (default: 9002)
quarkus.management.port=9003

# Disable management interface
quarkus.management.enabled=false
```

### Filter HTTP Metrics

Ignore specific endpoints from HTTP metrics:

```properties
quarkus.micrometer.binder.http-server.ignore-patterns=/health,/q/.*
```

## Best Practices

1. **Use Rate Functions**: For counters, always use `rate()` or `increase()` functions in PromQL
2. **Set Appropriate Time Windows**: Use 5m for real-time monitoring, longer windows for trends
3. **Create Composite Metrics**: Combine metrics to create meaningful ratios (e.g., success rates)
4. **Set Up Alerts**: Don't just collect metrics—alert on anomalies
5. **Monitor Trends**: Track metrics over time to identify patterns
6. **Correlate Metrics**: Look at multiple metrics together to understand system behavior

## Troubleshooting

### Metrics Not Appearing

1. Check management interface is enabled:
   ```bash
   curl http://localhost:9002/q/health
   ```

2. Verify Micrometer extension is installed:
   ```bash
   mvn dependency:tree | grep micrometer
   ```

3. Check application logs for initialization errors

### High Cardinality Issues

If you notice performance issues with metrics:

1. Review dimensional tags on custom metrics
2. Limit the number of unique tag values
3. Use `quarkus.micrometer.binder.http-server.match-patterns` to normalize URIs

### Prometheus Scrape Failures

1. Verify network connectivity to management port
2. Check firewall rules
3. Ensure Prometheus has correct target configuration
4. Verify metrics endpoint returns data: `curl http://localhost:9002/q/metrics`

## Additional Resources

- [Micrometer Documentation](https://micrometer.io/docs)
- [Quarkus Micrometer Guide](https://quarkus.io/guides/telemetry-micrometer)
- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/)
