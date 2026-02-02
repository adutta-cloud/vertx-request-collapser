# Troubleshooting Guide

## Performance Optimization

### Recent Improvements (Latest Version)

The system has been optimized for **maximum performance**:

✅ **Leader-only fetching** - Only 1 API call to Producer (not 50)  
✅ **No artificial delays** - Followers check cache immediately  
✅ **Direct cache lookup** - No need to query 50 nodes  
✅ **Single cache entry** - Stores entire result set efficiently  

**Expected Performance:**
- **First request (cache miss)**: ~50-150ms (includes Producer fetch)
- **Subsequent requests (cache hit)**: ~5-20ms (direct cache lookup)
- **With 5M rows**: Should scale linearly with data size from Producer

---

## 404 Error: Producer Service Not Found

### Issue
```
ERROR com.griddynamics.consumer.ConsumerVerticle -- Producer service returned non-200 status: 404
```

### Root Cause
The Consumer service is trying to connect to the Producer service at `http://localhost:8080/data`, but receiving a 404 (Not Found) response.

### Possible Causes

1. **Producer Service Not Running**
   - The Producer service must be running on port 8080
   - Check if the Producer process is active

2. **Incorrect Endpoint**
   - The Consumer is calling `/data` endpoint
   - Verify the Producer service has this endpoint configured
   - Check Producer service documentation for correct endpoint path

3. **Port Mismatch**
   - Consumer expects Producer on port 8080
   - Verify Producer is actually running on port 8080

### How to Diagnose

#### 1. Check Producer Service Status
```bash
# Check if port 8080 is listening
lsof -i :8080

# Or on Linux
netstat -an | grep 8080
```

#### 2. Test Producer Endpoint Directly
```bash
# Test if Producer service responds
curl http://localhost:8080/data?key=test

# Or check the root endpoint
curl http://localhost:8080/
```

#### 3. Use the Health Check Endpoint
```bash
# Check Consumer service health (includes Producer connectivity check)
curl http://localhost:8081/health
```

Expected response:
```json
{
  "status": "UP",
  "consumer_service": "healthy",
  "hazelcast_cluster": 1,
  "producer_service": "connected",
  "producer_status": 200,
  "timestamp": 1738444117412
}
```

If Producer is down:
```json
{
  "status": "UP",
  "consumer_service": "healthy",
  "hazelcast_cluster": 1,
  "producer_service": "disconnected",
  "producer_error": "Connection refused: localhost/127.0.0.1:8080",
  "timestamp": 1738444117412
}
```

### Solutions

#### Solution 1: Start the Producer Service
```bash
# Navigate to producer service directory
cd /path/to/producer-service

# Start the Producer service
# (exact command depends on your Producer implementation)
mvn clean compile exec:java
# OR
java -jar producer-service.jar
```

#### Solution 2: Update Consumer Configuration
If the Producer endpoint is different, update `ConsumerVerticle.java`:

```java
// Change from:
webClient.get(8080, "localhost", "/data")

// To the correct endpoint:
webClient.get(8080, "localhost", "/your-correct-endpoint")
```

#### Solution 3: Configuration File (Future Enhancement)
Consider externalizing the configuration to `application.properties`:

```properties
producer.service.host=localhost
producer.service.port=8080
producer.service.endpoint=/data
```

### Verification Steps

1. **Start Producer Service**
   ```bash
   # Terminal 1: Start Producer
   cd producer-service
   mvn clean compile exec:java
   ```

2. **Start Consumer Service**
   ```bash
   # Terminal 2: Start Consumer
   cd consumer-service
   mvn clean compile exec:java
   ```

3. **Check Health**
   ```bash
   # Terminal 3: Verify connectivity
   curl http://localhost:8081/health | jq
   ```

4. **Test Search**
   ```bash
   curl "http://localhost:8081/search?key=test" | jq
   ```

### Expected Behavior

✅ **Successful Flow (First Request - Cache Miss):**
```
21:48:37 [INFO] Node Consumer-Node-117622618638875 elected as LEADER
21:48:37 [DEBUG] Current node is leader, fetching data from Producer
21:48:37 [DEBUG] Fetching data from Producer service for key: test
21:48:37 [INFO] Successfully cached 1000 items for key: test
21:48:37 [INFO] Search completed for key: test | Duration: 85ms | Found: true
```

**Response:**
```json
{
  "searched_for": "test",
  "found": true,
  "response_time_ms": 85,
  "data": "[{...1000 items...}]",
  "timestamp": 1738444117497
}
```

✅ **Successful Flow (Subsequent Request - Cache Hit):**
```
21:48:38 [DEBUG] Node Consumer-Node-117622618638875 joined cluster | Current leader: Consumer-Node-117622618638875
21:48:38 [DEBUG] Current node is follower, checking cache
21:48:38 [INFO] Search completed for key: test | Duration: 8ms | Found: true
```

**Response:**
```json
{
  "searched_for": "test",
  "found": true,
  "response_time_ms": 8,
  "data": "[{...cached items...}]",
  "timestamp": 1738444118505
}
```

❌ **Error Flow (Producer Down):**
```
21:48:37 [INFO] Node Consumer-Node-117622618638875 elected as LEADER
21:48:37 [DEBUG] Current node is leader, fetching data from Producer
21:48:37 [DEBUG] Fetching data from Producer service for key: test
21:48:37 [ERROR] Producer service returned non-200 status: 404
21:48:37 [ERROR] Leader failed to fetch data from Producer for key: test
```

### Production Recommendations

1. **Circuit Breaker Pattern**
   - Implement circuit breaker for Producer service calls
   - Use Resilience4j or similar library

2. **Retry Mechanism**
   - Add exponential backoff retry logic
   - Configure max retry attempts

3. **Service Discovery**
   - Use Consul, Eureka, or Kubernetes service discovery
   - Dynamic Producer endpoint resolution

4. **Monitoring & Alerts**
   - Set up alerts for Producer service availability
   - Monitor `/health` endpoint with Prometheus/Grafana
   - Track 404 error rates

5. **Fallback Strategy**
   - Return cached data if available
   - Provide graceful degradation

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/search?key=<value>` | GET | Search for data across 50 nodes with caching |
| `/health` | GET | Health check including Producer connectivity |

### Error Codes

| Status Code | Meaning | Action |
|-------------|---------|--------|
| 200 | Success | Data found and returned |
| 400 | Bad Request | Missing 'key' parameter |
| 503 | Service Unavailable | Producer service is down or unreachable |

---

## Need Help?

If the issue persists:
1. Check logs in `consumer-service/logs/`
2. Verify network connectivity between services
3. Review firewall rules
4. Check if Producer service is behind a proxy
5. Verify Hazelcast cluster is properly formed

For more details, see the main [README.md](../README.md)
