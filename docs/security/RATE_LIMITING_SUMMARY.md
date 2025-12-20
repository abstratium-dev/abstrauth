# Rate Limiting

## What Was Implemented

**New Files:**
1. `RateLimitFilter.java` - Request filter that enforces rate limits
2. `RateLimitResponseFilter.java` - Response filter that adds rate limit headers

## How It Works

**Sliding Window Algorithm:**
1. Tracks requests per IP address
2. Allows N requests per time window
3. Bans IP temporarily after exceeding limits
4. Uses in-memory storage (suitable for single-instance deployments)

## Default Configuration

```properties
rate-limit.enabled=true
rate-limit.oauth.max-requests=10          # 10 requests per window
rate-limit.oauth.window-seconds=60        # 60 seconds (1 minute)
rate-limit.oauth.ban-duration-seconds=300 # 5 minutes ban
```

**This means:**
- Each IP can make **10 requests per minute**
- After exceeding the limit, the IP is **banned for 5 minutes**
- Rate limits apply **only to OAuth/auth endpoints**

## Protected Endpoints

Rate limiting is applied to:
- `/oauth2/authorize` - Authorization initiation
- `/oauth2/token` - Token exchange
- `/oauth2/callback/*` - OAuth callbacks
- `/oauth2/federated/*` - Federated login
- `/api/signup` - User registration
- `/api/signin` - User authentication

## Rate Limit Headers

Clients receive informational headers:

```http
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
X-RateLimit-Reset: 1733174400
```

## Rate Limit Response

When limit is exceeded:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 300
X-RateLimit-Limit: 10
X-RateLimit-Window: 60

Rate limit exceeded. Too many requests from your IP address.
```

## IP Address Detection

The rate limiter detects client IP from:
1. **X-Forwarded-For** header (when behind reverse proxy)
2. **X-Real-IP** header (alternative proxy header)
3. **Remote address** (fallback)

**Important for Nginx Deployment:**

```nginx
location / {
    proxy_pass http://localhost:8080;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header Host $host;
}
```

## Testing Rate Limiting

**Manual Test:**

```bash
# Make 11 requests quickly (exceeds limit of 10)
for i in {1..11}; do
  curl -i http://localhost:8080/oauth2/authorize?client_id=test
  echo "Request $i"
done
```

**Expected Result:**
- First 10 requests: HTTP 200/302 with rate limit headers
- 11th request: HTTP 429 with "Rate limit exceeded" message

**Check Headers:**
```bash
curl -i http://localhost:8080/oauth2/authorize?client_id=test | grep -i rate
```

**Expected:**
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 9
X-RateLimit-Reset: 1733174400
```

---

# Production Recommendations

## 1. Stricter Rate Limits

For production, consider tighter limits:

```properties
%prod.rate-limit.oauth.max-requests=5
%prod.rate-limit.oauth.window-seconds=60
%prod.rate-limit.oauth.ban-duration-seconds=600
```

## 2. Enable HSTS (HTTPS Only)

Uncomment in `SecurityHeadersFilter.java` when using HTTPS:

```java
responseContext.getHeaders().add("Strict-Transport-Security", 
    "max-age=31536000; includeSubDomains; preload");
```

## 3. Nginx-Level Rate Limiting

For multi-instance deployments, also implement rate limiting at nginx:

```nginx
# Define rate limit zone (10MB can track ~160,000 IPs)
limit_req_zone $binary_remote_addr zone=oauth_limit:10m rate=5r/m;

location /oauth2/ {
    limit_req zone=oauth_limit burst=10 nodelay;
    proxy_pass http://localhost:8080;
}
```

**Benefits of Nginx Rate Limiting:**
- Works across multiple application instances
- Protects before requests reach the application
- More efficient for high-traffic scenarios

## 4. Distributed Rate Limiting

For **multi-instance deployments**, the current in-memory implementation has limitations:
- Each instance tracks rate limits independently
- An attacker could bypass limits by hitting different instances

**Solutions:**
1. **Use Redis** for shared rate limit storage
2. **Implement at nginx/load balancer** level
3. **Use a dedicated service** like Kong or API Gateway

## 5. Monitoring

Add logging to track rate limit violations:

```java
// In RateLimitFilter.java
private static final Logger logger = Logger.getLogger(RateLimitFilter.class);

// In isRateLimited() method
if (tracker.getCount() >= maxRequests) {
    logger.warn("Rate limit exceeded for IP: " + ip);
    return true;
}
```

---

# Security Benefits

## Defense Against Common Attacks

| Attack Type | Protection | Implementation |
|-------------|------------|----------------|
| **XSS (Cross-Site Scripting)** | CSP prevents execution of unauthorized scripts | `script-src 'self'` |
| **Clickjacking** | CSP and X-Frame-Options prevent iframe embedding | `frame-ancestors 'none'` |
| **MIME Sniffing** | X-Content-Type-Options prevents type confusion | `nosniff` |
| **Brute Force** | Rate limiting prevents password guessing | 10 req/min limit |
| **Credential Stuffing** | Rate limiting slows down automated attacks | IP-based limits |
| **DoS (Denial of Service)** | Rate limiting prevents resource exhaustion | Temporary bans |

## Defense in Depth

Your application now has **multiple layers of security**:

1. ✅ **State Parameter Validation** - CSRF protection
2. ✅ **PKCE** - Authorization code interception protection
3. ✅ **CSP Headers** - XSS and code injection protection
4. ✅ **Rate Limiting** - Brute-force and DoS protection
5. ✅ **JWT Signature Verification** - Token integrity
6. ✅ **RBAC** - Authorization controls
7. ✅ **Short-Lived Codes** - Reduces attack window
8. ✅ **Redirect URI Validation** - Prevents code leakage

---

# Configuration Reference

# application.properties

```properties
# Rate Limiting
rate-limit.enabled=true
rate-limit.oauth.max-requests=10
rate-limit.oauth.window-seconds=60
rate-limit.oauth.ban-duration-seconds=300

# Production overrides (commented out)
# %prod.rate-limit.oauth.max-requests=5
# %prod.rate-limit.oauth.window-seconds=60
# %prod.rate-limit.oauth.ban-duration-seconds=600
```

---

## Optional Enhancements
- Implement CSP violation reporting endpoint
- Add Prometheus metrics for rate limiting
- Create dashboard to visualize rate limit violations
- Implement IP whitelist for trusted sources
- Add user-based rate limiting (in addition to IP-based)

