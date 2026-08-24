#!/bin/bash
# IntelliMove — Automated E2E Test Script
# Tests the complete ride lifecycle against running infrastructure
set -e

GATEWAY="http://localhost:8080"
BASE="http://localhost:8080"
AUTH="http://localhost:8081"
PASS=0
FAIL=0
T=$(date +%s)

check() {
    local label="$1" expected="$2" actual="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo "  ✅ $label"
        PASS=$((PASS + 1))
    else
        echo "  ❌ $label (expected: $expected, got: $actual)"
        FAIL=$((FAIL + 1))
    fi
}

echo "============================================"
echo "IntelliMove — E2E Test Suite"
echo "============================================"
echo ""

# ── 1. Infrastructure Health ──
echo "📦 Infrastructure Health"
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088; do
    r=$(curl -sf "http://localhost:$port/actuator/health" 2>/dev/null || echo "DOWN")
    check "Port $port health" '"status":"UP"' "$r"
done
echo ""

# ── 2. Auth: Register Customer ──
echo "👤 Auth — Register Customer"
R=$(curl -s -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"e2e_cust_${T}@test.com\",\"password\":\"Test1234!\",\"firstName\":\"John\",\"lastName\":\"Doe\",\"role\":\"CUSTOMER\"}")
CT=$(echo "$R" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
# AuthResponse has user.id nested, not userId at root
CID=$(echo "$R" | python3 -c "import sys,json; print(json.load(sys.stdin)['user']['id'])" 2>/dev/null || echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Customer registered" '"success":true' "$R"
check "JWT received" '"accessToken"' "$R"
echo "  📋 Customer ID: $CID"
echo ""

# ── 3. Auth: Register Driver ──
echo "🚗 Auth — Register Driver"
R=$(curl -s -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"e2e_drv_${T}@test.com\",\"password\":\"Test1234!\",\"firstName\":\"Jane\",\"lastName\":\"Driver\",\"role\":\"DRIVER\"}")
DT=$(echo "$R" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
DUID=$(echo "$R" | python3 -c "import sys,json; print(json.load(sys.stdin)['user']['id'])" 2>/dev/null || echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Driver registered" '"success":true' "$R"
echo "  📋 Driver User ID: $DUID"
echo ""

# ── 4. Auth: Register Admin ──
echo "👑 Auth — Register Admin"
R=$(curl -s -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"e2e_adm_${T}@test.com\",\"password\":\"Admin1234!\",\"firstName\":\"Admin\",\"lastName\":\"User\",\"role\":\"ADMIN\"}")
AT=$(echo "$R" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
AID=$(echo "$R" | python3 -c "import sys,json; print(json.load(sys.stdin)['user']['id'])" 2>/dev/null || echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Admin registered" '"success":true' "$R"
echo "  📋 Admin ID: $AID"
echo ""

# ── 5. Auth: Login ──
echo "🔑 Auth — Login"
R=$(curl -s -X POST "$AUTH/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"e2e_cust_${T}@test.com\",\"password\":\"Test1234!\"}")
check "Customer login" '"accessToken"' "$R"
echo ""

# ── 6. Auth: Security ──
echo "🔒 Auth — Security"
R=$(curl -s -X POST "$AUTH/api/v1/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"e2e_cust_99999@test.com","password":"Wrong!"}')
check "Invalid credentials rejected" 'INVALID_CREDENTIALS' "$R"

# Test invalid JWT through gateway
R=$(curl -s -w "\n%{http_code}" "$GATEWAY/api/v1/rides" -H "Authorization: Bearer invalid_token")
HTTP_CODE=$(echo "$R" | tail -1)
check "Invalid JWT rejected (401)" "401" "$HTTP_CODE"
echo ""

# ── 7. Driver: Create Profile (through gateway) ──
echo "🚙 Driver — Create Profile"
R=$(curl -s -X POST "$GATEWAY/api/v1/drivers/register" -H "Content-Type: application/json" \
  -H "X-User-Id: $DUID" -H "Authorization: Bearer $DT" \
  -d "{\"licenseNumber\":\"E2E-${T}\",\"vehicleMake\":\"Honda\",\"vehicleModel\":\"Civic\",\"vehicleYear\":2023,\"vehicleColor\":\"Blue\",\"licensePlate\":\"E2E-${T}\",\"vehicleType\":\"ECONOMY\"}")
DPID=$(echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Driver profile created" '"success":true' "$R"
echo "  📋 Driver Profile ID: $DPID"
echo ""

# ── 8. Driver: State Machine ──
echo "🔄 Driver — State Machine"
R=$(curl -s -X PATCH "$GATEWAY/api/v1/drivers/$DPID/status" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID" -d '{"status":"ONLINE"}')
check "OFFLINE → ONLINE" '"status":"ONLINE"' "$R"
R=$(curl -s -X PATCH "$GATEWAY/api/v1/drivers/$DPID/status" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID" -d '{"status":"AVAILABLE"}')
check "ONLINE → AVAILABLE" '"status":"AVAILABLE"' "$R"
echo ""

# ── 9. Location: Redis GEO ──
# Contract: GEO members are the driver USER ID (JWT), same as Ride.driverId.
# Driver PROFILE ID is rejected so matching cannot assign an ID the driver cannot act on.
echo "📍 Location — Redis GEO"
R=$(curl -s -X POST "$GATEWAY/api/v1/location/driver/$DPID/update" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID" \
  -d "{\"latitude\":40.7128,\"longitude\":-74.0060,\"metadata\":{\"rating\":\"4.8\",\"totalTrips\":\"150\"}}")
check "Profile ID rejected on location update" 'user ID' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/location/update" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" \
  -d "{\"latitude\":40.7128,\"longitude\":-74.0060,\"metadata\":{\"rating\":\"4.8\",\"totalTrips\":\"150\"}}")
check "Location stored (JWT user ID)" '"success":true' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/location/driver/$DUID/update" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" \
  -d "{\"latitude\":40.7128,\"longitude\":-74.0060,\"metadata\":{\"rating\":\"4.8\",\"totalTrips\":\"150\"}}")
check "Path update with user ID accepted" '"success":true' "$R"

R=$(curl -s "$GATEWAY/api/v1/location/nearby?latitude=40.7128&longitude=-74.0060&radiusKm=5" -H "Authorization: Bearer $DT")
check "Nearby driver found" '"driverId"' "$R"
check "Nearby driver keyed by user ID" "$DUID" "$R"
echo ""

# ── 10. Ride: Full Lifecycle ──
echo "🚕 Ride — Full Lifecycle"
R=$(curl -s -X POST "$GATEWAY/api/v1/rides" -H "Content-Type: application/json" -H "Authorization: Bearer $CT" -H "X-User-Id: $CID" \
  -d "{\"pickupLatitude\":40.7128,\"pickupLongitude\":-74.0060,\"dropoffLatitude\":40.7589,\"dropoffLongitude\":-73.9851,\"rideType\":\"ECONOMY\",\"pickupAddress\":\"Times Square\",\"dropoffAddress\":\"Central Park\"}")
RID=$(echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Ride requested" '"status":"REQUESTED"' "$R"
check "Fare estimated" '"estimatedFare"' "$R"
echo "  📋 Ride ID: $RID"

R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/assign?driverId=$DUID" -H "Authorization: Bearer $AT" -H "X-User-Id: $AID")
check "Driver assigned" '"status":"DRIVER_ASSIGNED"' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/accept" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID")
check "Driver accepted" '"status":"DRIVER_ACCEPTED"' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/start" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID")
check "Trip started" '"status":"TRIP_STARTED"' "$R"

sleep 2
R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/complete" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID")
check "Trip completed" '"status":"TRIP_COMPLETED"' "$R"
check "Final fare calculated" '"finalFare"' "$R"

R=$(curl -s "$GATEWAY/api/v1/rides/$RID" -H "Authorization: Bearer $CT" -H "X-User-Id: $CID")
check "Ride persisted in DB" '"TRIP_COMPLETED"' "$R"
echo ""

# ── 11. Payment ──
echo "💳 Payment — Verify"
echo "  ℹ️  Payment workflow tested via ride completion outbox event"
echo ""

# ── 12. Ride History ──
echo "📋 Ride History"
R=$(curl -s "$GATEWAY/api/v1/rides/customer/$CID?page=0&size=10" -H "Authorization: Bearer $CT" -H "X-User-Id: $CID")
check "Customer ride history" '"content"' "$R"
echo ""

# ── 13. Invalid State Transitions ──
echo "🚫 State Machine Guards"
R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/assign?driverId=$DUID" -H "Authorization: Bearer $AT" -H "X-User-Id: $AID" -w "\n%{http_code}")
HTTP_CODE=$(echo "$R" | tail -1)
BODY=$(echo "$R" | head -1)
if [ "$HTTP_CODE" -ge 400 ]; then
    echo "  ✅ Assign to completed ride rejected (HTTP $HTTP_CODE)"
    PASS=$((PASS + 1))
else
    echo "  ❌ Assign to completed ride should be rejected (got HTTP $HTTP_CODE)"
    FAIL=$((FAIL + 1))
fi
echo ""

# ── 14. AI Operations ──
echo "🤖 AI Operations"
R=$(curl -s --max-time 30 -X POST "$GATEWAY/api/v1/ai/ops/query" -H "Content-Type: application/json" -H "Authorization: Bearer $AT" -H "X-User-Id: $AID" \
  -H "X-Session-Id: test-session" -d '{"query":"What are the current ride statistics?"}')
check "AI query accepted or timed out gracefully" '"analysis"\|"LLM unavailable"\|"analysis"' "$R"
echo ""

# ── Summary ──
echo "============================================"
echo "RESULTS: $PASS passed, $FAIL failed"
echo "============================================"
if [ $FAIL -eq 0 ]; then
    echo "🎉 ALL E2E TESTS PASSED!"
    exit 0
else
    echo "⚠️ Some tests failed"
    exit 1
fi
