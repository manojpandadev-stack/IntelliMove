#!/bin/bash
# Start all IntelliMove services

export JWT_SECRET=changeme_jwt_secret_min_32_chars_long!!
export POSTGRES_HOST=localhost
export POSTGRES_DB=intellimove
export POSTGRES_USER=intellimove
export POSTGRES_PASSWORD=changeme_postgres
export REDIS_HOST=localhost
export KAFKA_BROKERS=localhost:9092
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

echo "Starting Auth Service..."
nohup java -jar intellimove-auth/target/intellimove-auth-1.0.0-SNAPSHOT.jar \
  --AUTH_SERVICE_PORT=8081 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/intellimove_auth \
  > /tmp/auth.log 2>&1 &
echo "  PID=$!"

sleep 3

echo "Starting User Service..."
nohup java -jar intellimove-user/target/intellimove-user-1.0.0-SNAPSHOT.jar \
  --USER_SERVICE_PORT=8082 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/intellimove_user \
  > /tmp/user.log 2>&1 &
echo "  PID=$!"

echo "Starting Driver Service..."
nohup java -jar intellimove-driver/target/intellimove-driver-1.0.0-SNAPSHOT.jar \
  --DRIVER_SERVICE_PORT=8083 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/intellimove_driver \
  > /tmp/driver.log 2>&1 &
echo "  PID=$!"

echo "Starting Ride Service..."
nohup java -jar intellimove-ride/target/intellimove-ride-1.0.0-SNAPSHOT.jar \
  --RIDE_SERVICE_PORT=8084 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/intellimove_ride \
  > /tmp/ride.log 2>&1 &
echo "  PID=$!"

echo "Starting Location Service..."
nohup java -jar intellimove-location/target/intellimove-location-1.0.0-SNAPSHOT.jar \
  --LOCATION_SERVICE_PORT=8085 \
  > /tmp/location.log 2>&1 &
echo "  PID=$!"

echo "Starting Payment Service..."
nohup java -jar intellimove-payment/target/intellimove-payment-1.0.0-SNAPSHOT.jar \
  --PAYMENT_SERVICE_PORT=8086 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/intellimove_payment \
  > /tmp/payment.log 2>&1 &
echo "  PID=$!"

echo "Starting Notification Service..."
nohup java -jar intellimove-notification/target/intellimove-notification-1.0.0-SNAPSHOT.jar \
  --NOTIFICATION_SERVICE_PORT=8087 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/intellimove_notification \
  > /tmp/notification.log 2>&1 &
echo "  PID=$!"

echo "Starting AI Ops Service..."
nohup java -jar intellimove-ai-ops/target/intellimove-ai-ops-1.0.0-SNAPSHOT.jar \
  --AI_OPS_SERVICE_PORT=8088 \
  --ai.ops.llm-enabled=false \
  --ai.ops.timeout-seconds=30 \
  > /tmp/aiops.log 2>&1 &
echo "  PID=$!"

echo "Starting Gateway..."
nohup java -jar intellimove-gateway/target/intellimove-gateway-1.0.0-SNAPSHOT.jar \
  --GATEWAY_PORT=8080 \
  > /tmp/gateway.log 2>&1 &
echo "  PID=$!"

echo ""
echo "All services starting... wait 30s then run: bash check-health.sh"
