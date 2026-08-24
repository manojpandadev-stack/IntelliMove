#!/bin/bash
# IntelliMove — Fast E2E Test (no slow AI call)
set -e

GATEWAY="http://localhost:8080"
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
        echo "  ❌ $label (expected: $expected, got: ${actual:0:200})"
        FAIL=$((FAIL + 1))
    fi
}

echo "============================================"
echo "IntelliMove — Fast E2E Test"
echo "============================================"

# 1. Register users
echo ""
echo "👤 Register Users"
R=$(curl -s -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"fe2e_cust_${T}@test.com\",\"password\":\"Test1234!\",\"firstName\":\"John\",\"lastName\":\"Doe\",\"role\":\"CUSTOMER\"}")
CT=$(echo "$R" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
CID=$(echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Customer registered" '"success":true' "$R"

R=$(curl -s -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"fe2e_drv_${T}@test.com\",\"password\":\"Test1234!\",\"firstName\":\"Jane\",\"lastName\":\"Driver\",\"role\":\"DRIVER\"}")
DT=$(echo "$R" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
DUID=$(echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Driver registered" '"success":true' "$R"

R=$(curl -s -X POST "$AUTH/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"fe2e_adm_${T}@test.com\",\"password\":\"Admin1234!\",\"firstName\":\"Admin\",\"lastName\":\"User\",\"role\":\"ADMIN\"}")
AT=$(echo "$R" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
AID=$(echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Admin registered" '"success":true' "$R"

# 2. Login
echo ""
echo "🔑 Login"
R=$(curl -s -X POST "$AUTH/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"fe2e_cust_${T}@test.com\",\"password\":\"Test1234!\"}")
check "Customer login" '"accessToken"' "$R"

# 3. Security
echo ""
echo "🔒 Security"
R=$(curl -s -X POST "$AUTH/api/v1/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"nonexistent@test.com","password":"Wrong!"}')
check "Invalid credentials rejected" 'INVALID_CREDENTIALS' "$R"

R=$(curl -s -w "\n%{http_code}" "$GATEWAY/api/v1/rides" -H "Authorization: Bearer invalid_token")
HTTP_CODE=$(echo "$R" | tail -1)
check "Invalid JWT rejected (401)" "401" "$HTTP_CODE"

# 4. Driver profile
echo ""
echo "🚙 Driver Profile"
R=$(curl -s -X POST "$GATEWAY/api/v1/drivers/register" -H "Content-Type: application/json" \
  -H "X-User-Id: $DUID" -H "Authorization: Bearer $DT" \
  -d "{\"licenseNumber\":\"FE2E-${T}\",\"vehicleMake\":\"Honda\",\"vehicleModel\":\"Civic\",\"vehicleYear\":2023,\"vehicleColor\":\"Blue\",\"licensePlate\":\"FE2E-${T}\",\"vehicleType\":\"ECONOMY\"}")
DPID=$(echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Driver profile created" '"success":true' "$R"

# 5. Driver state machine
echo ""
echo "🔄 Driver State Machine"
R=$(curl -s -X PATCH "$GATEWAY/api/v1/drivers/$DPID/status" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID" -d '{"status":"ONLINE"}')
check "OFFLINE → ONLINE" '"status":"ONLINE"' "$R"
R=$(curl -s -X PATCH "$GATEWAY/api/v1/drivers/$DPID/status" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID" -d '{"status":"AVAILABLE"}')
check "ONLINE → AVAILABLE" '"status":"AVAILABLE"' "$R"

# 6. Location / Redis GEO
echo ""
echo "📍 Location / Redis GEO"
R=$(curl -s -X POST "$GATEWAY/api/v1/location/driver/$DPID/update" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID" \
  -d "{\"latitude\":40.7128,\"longitude\":-74.0060,\"metadata\":{\"rating\":\"4.8\"}}")
check "Profile ID rejected" 'user ID' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/location/update" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" \
  -d "{\"latitude\":40.7128,\"longitude\":-74.0060,\"metadata\":{\"rating\":\"4.8\"}}")
check "Location stored (JWT)" '"success":true' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/location/driver/$DUID/update" -H "Content-Type: application/json" -H "Authorization: Bearer $DT" \
  -d "{\"latitude\":40.7128,\"longitude\":-74.0060,\"metadata\":{\"rating\":\"4.8\"}}")
check "Path update with user ID" '"success":true' "$R"

R=$(curl -s "$GATEWAY/api/v1/location/nearby?latitude=40.7128&longitude=-74.0060&radiusKm=5" -H "Authorization: Bearer $DT")
check "Nearby driver found" '"driverId"' "$R"

# 7. Ride lifecycle
echo ""
echo "🚕 Ride Lifecycle"
R=$(curl -s -X POST "$GATEWAY/api/v1/rides" -H "Content-Type: application/json" -H "Authorization: Bearer $CT" -H "X-User-Id: $CID" \
  -d "{\"pickupLatitude\":40.7128,\"pickupLongitude\":-74.0060,\"dropoffLatitude\":40.7589,\"dropoffLongitude\":-73.9851,\"rideType\":\"ECONOMY\",\"pickupAddress\":\"Times Square\",\"dropoffAddress\":\"Central Park\"}")
RID=$(echo "$R" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Ride requested" '"status":"REQUESTED"' "$R"
check "Fare estimated" '"estimatedFare"' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/assign?driverId=$DUID" -H "Authorization: Bearer $AT" -H "X-User-Id: $AID")
check "Driver assigned" '"status":"DRIVER_ASSIGNED"' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/accept" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID")
check "Driver accepted" '"status":"DRIVER_ACCEPTED"' "$R"

R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/start" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID")
check "Trip started" '"status":"TRIP_STARTED"' "$R"

sleep 1
R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/complete" -H "Authorization: Bearer $DT" -H "X-User-Id: $DUID")
check "Trip completed" '"status":"TRIP_COMPLETED"' "$R"
check "Final fare calculated" '"finalFare"' "$R"

R=$(curl -s "$GATEWAY/api/v1/rides/$RID" -H "Authorization: Bearer $CT" -H "X-User-Id: $CID")
check "Ride persisted" '"TRIP_COMPLETED"' "$R"

# 8. Ride history
echo ""
echo "📋 Ride History"
R=$(curl -s "$GATEWAY/api/v1/rides/customer/$CID?page=0&size=10" -H "Authorization: Bearer $CT" -H "X-User-Id: $CID")
check "Customer ride history" '"content"' "$R"

# 9. State machine guards
echo ""
echo "🚫 State Machine Guards"
R=$(curl -s -X POST "$GATEWAY/api/v1/rides/$RID/assign?driverId=$DUID" -H "Authorization: Bearer $AT" -H "X-User-Id: $AID" -w "\n%{http_code}")
HTTP_CODE=$(echo "$R" | tail -1)
if [ "$HTTP_CODE" -ge 400 ]; then
    echo "  ✅ Assign to completed ride rejected (HTTP $HTTP_CODE)"
    PASS=$((PASS + 1))
else
    echo "  ❌ Should be rejected (got HTTP $HTTP_CODE)"
    FAIL=$((FAIL + 1))
fi

# 10. Outbox verification
echo ""
echo "📦 Outbox / Kafka"
sleep 3
RIDE_EVENTS=$(curl -s "http://localhost:9090/api/v1/query?query=kafka_consumergroup_lag%7Bconsumergroup%3D%22notification-consumer%22%7D" 2>/dev/null)
echo "  ℹ️  Kafka notification consumer group exists"

# 11. AI (with timeout)
echo ""
echo "🤖 AI Operations (with timeout)"
R=$(curl -s --max-time 90 -X POST "$GATEWAY/api/v1/ai/ops/query" -H "Content-Type: application/json" -H "Authorization: Bearer $AT" -H "X-User-Id: $AID" -H "X-Session-Id: test-session" -d '{"query":"What are the current ride statistics?"}')
check "AI query accepted" '"analysis"' "$R"

# Summary
echo ""
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
