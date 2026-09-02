import { test, expect, type Page } from '@playwright/test';

/**
 * REAL-TIME DRIVER LOCATION — active-ride map verification.
 *
 * Uses ONLY real backend flows:
 * - driver registers + goes ONLINE + reports GPS via POST /api/v1/location/update
 * - auto-matching assigns the driver (Kafka consumer + Redis GEO)
 * - once DRIVER_ASSIGNED, the driver's subsequent GPS updates (with rideId)
 *   are broadcast over /ws/location to the subscribed rider
 * - the rider map marker must show those EXACT backend coordinates
 *
 * A second scenario force-closes the WebSocket (routeWebSocket) to prove the
 * Redis-GEO REST polling fallback keeps the live marker working and the
 * active ride unaffected.
 */

const GW = 'http://localhost:8080';
const PASSWORD = 'TestPass123!';

// Far from every other spec's NYC drivers so auto-matching deterministically
// picks THIS spec's driver (geocoding is mocked anyway).
const PICKUP = { lat: 48.8566, lng: 2.3522 };
const DROPOFF = { lat: 48.8666, lng: 2.3622 };
const DRIVER_START = { lat: 48.8567, lng: 2.3523 };

interface Creds {
  email: string;
  token: string;
  userId: string;
}

async function apiRegister(page: Page, email: string, body: Record<string, unknown>): Promise<Creds> {
  const r = await page.request.post(`${GW}/api/v1/auth/register`, {
    data: { ...body, email, password: PASSWORD },
  });
  expect(r.status()).toBeLessThan(300);
  const data = (await r.json()).data;
  return { email, token: data.accessToken, userId: data.user?.id ?? data.userId };
}

async function loginAs(page: Page, creds: Creds) {
  await page.addInitScript(
    ([t, u]: unknown[]) => {
      window.localStorage.setItem('accessToken', t as string);
      window.localStorage.setItem('user', JSON.stringify(u));
    },
    [creds.token, { id: creds.userId, email: creds.email, firstName: 'Resp', lastName: 'Customer', role: 'CUSTOMER' }],
  );
}

function mockGeocode(page: Page) {
  return page.route('**/geocode/search**', async (route) => {
    const url = new URL(route.request().url());
    const q = url.searchParams.get('q') ?? '';
    const hits = q.toLowerCase().includes('manhattan')
      ? [{ lat: String(DROPOFF.lat), lon: String(DROPOFF.lng), display_name: 'Manhattan, NY' }]
      : [{ lat: String(PICKUP.lat), lon: String(PICKUP.lng), display_name: 'Paris Ave, NY' }];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(hits) });
  });
}

/** Registers an ONLINE driver whose Redis GEO position sits at the pickup. */
async function createOnlineDriver(page: Page): Promise<Creds> {
  const suffix = Math.random().toString(36).slice(2, 7);
  const drv = await apiRegister(page, `drvloc_d_${Date.now()}_${suffix}@test.com`, {
    firstName: 'Diego', lastName: 'Location', role: 'DRIVER',
  });
  const pr = await page.request.post(`${GW}/api/v1/drivers/register`, {
    headers: { Authorization: `Bearer ${drv.token}` },
    data: {
      licenseNumber: `DL-${Date.now() % 1000000}${suffix}`,
      vehicleMake: 'Toyota', vehicleModel: 'Camry', vehicleYear: 2023,
      vehicleColor: 'Silver', licensePlate: `LC-${Date.now() % 100000}`,
      vehicleType: 'ECONOMY',
    },
  });
  expect(pr.status()).toBeLessThan(300);
  const profileId = ((await pr.json()).data as { id: string }).id;

  const st = await page.request.patch(`${GW}/api/v1/drivers/${profileId}/status`, {
    headers: { Authorization: `Bearer ${drv.token}` },
    data: { status: 'ONLINE' },
  });
  expect(st.status()).toBeLessThan(300);

  // Initial GPS heartbeat near the pickup → eligible for Redis GEO matching.
  const loc = await page.request.post(`${GW}/api/v1/location/update`, {
    headers: { Authorization: `Bearer ${drv.token}` },
    data: { latitude: DRIVER_START.lat, longitude: DRIVER_START.lng },
  });
  expect(loc.status()).toBeLessThan(300);
  return drv;
}

async function bookRideInUi(page: Page) {
  await mockGeocode(page);
  await page.goto('/dashboard');
  await expect(page.getByTestId('booking-panel')).toBeVisible();

  const pickup = page.getByTestId('location-input-map-pin');
  await pickup.fill('Paris Ave, NY');
  await page.getByRole('option').first().click({ timeout: 15_000 });

  const dropoff = page.getByTestId('location-input-flag');
  await dropoff.fill('Manhattan, NY');
  await page.getByRole('option').first().click({ timeout: 15_000 });

  await expect(page.getByTestId('option-ECONOMY')).toBeVisible({ timeout: 15_000 });
  await page.getByTestId('request-ride').click();
  await expect(page.getByTestId('active-ride-card')).toBeVisible({ timeout: 20_000 });

  // Auto-match assigns OUR driver (only GEO-fresh driver near these coords).
  const badge = page.locator('[data-testid="active-ride-card"] .im-badge[data-status]');
  await expect(badge).toHaveAttribute('data-status', 'DRIVER_ASSIGNED', { timeout: 60_000 });
}

interface AssignedRide {
  id: string;
  status: string;
  driverId?: string;
}

async function getAssignedRide(page: Page, customer: Creds): Promise<AssignedRide> {
  const ridesRes = await page.request.get(`${GW}/api/v1/rides/customer/${customer.userId}`, {
    headers: { Authorization: `Bearer ${customer.token}` },
  });
  const rides = (await ridesRes.json()).data.content as AssignedRide[];
  const ride = rides.find((r) => r.status === 'DRIVER_ASSIGNED');
  expect(ride, 'ride must be DRIVER_ASSIGNED with a driver').toBeTruthy();
  return ride!;
}

/** Driver GPS update carrying rideId → triggers the WS broadcast path. */
async function driverReportLocation(
  page: Page, driver: Creds, rideId: string, lat: number, lng: number,
) {
  const res = await page.request.post(`${GW}/api/v1/location/update`, {
    headers: { Authorization: `Bearer ${driver.token}` },
    data: { latitude: lat, longitude: lng, rideId },
  });
  expect(res.status()).toBeLessThan(300);
}

async function cleanupActiveRides(page: Page, customer: Creds) {
  const ridesRes = await page.request.get(`${GW}/api/v1/rides/customer/${customer.userId}`, {
    headers: { Authorization: `Bearer ${customer.token}` },
  });
  const rides = (await ridesRes.json()).data?.content ?? [];
  for (const ride of rides) {
    if (['REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING'].includes(ride.status)) {
      await page.request.post(`${GW}/api/v1/rides/${ride.id}/cancel`, {
        headers: { Authorization: `Bearer ${customer.token}` },
        data: { reason: 'RIDER_CANCELLED', note: 'driver-location spec cleanup' },
      });
    }
  }
}

/** Asserts the driver marker sits within Redis-GEO precision of the
 * coordinates the driver actually reported (geohash ≈ sub-cm error). */
async function expectMarkerNear(page: Page, lat: number, lng: number, timeout = 20_000) {
  const marker = page.getByTestId('map-marker-driver');
  await expect(marker).toBeAttached({ timeout });
  await expect
    .poll(
      async () => {
        const raw = (await marker.getAttribute('data-coords')) ?? '';
        const [la, lo] = raw.split(',').map(Number);
        return Number.isFinite(la) && Math.abs(la - lat) < 0.0005 && Math.abs(lo - lng) < 0.001;
      },
      { timeout, message: `marker should be near ${lat},${lng}` },
    )
    .toBe(true);
  return marker;
}

test.describe.configure({ mode: 'serial' });

test.describe('Active-ride driver location (real backend)', () => {
  test.setTimeout(150_000);

  let customer: Creds;
  let driver: Creds;

  test.beforeEach(async ({ page }) => {
    const suffix = Math.random().toString(36).slice(2, 7);
    customer = await apiRegister(page, `drvloc_c_${Date.now()}_${suffix}@test.com`, {
      firstName: 'Resp', lastName: 'Customer', role: 'CUSTOMER',
    });
    driver = await createOnlineDriver(page);
    await loginAs(page, customer);
  });

  test.afterEach(async ({ page }) => {
    await cleanupActiveRides(page, customer);
  });

  test('rider map marker shows the driver at actual backend coordinates and tracks updates', async ({ page }) => {
    await bookRideInUi(page);

    // The assigned driver identity comes from the real ride record.
    const ride = await getAssignedRide(page, customer);
    expect(ride.driverId).toBeTruthy();

    // Driver GPS update WITH rideId → Location Service broadcasts over WS.
    await driverReportLocation(page, driver, ride.id, 48.857, 2.353);

    // Marker must render at EXACTLY the backend-reported coordinates
    // (within Redis GEO geohash precision).
    const marker = await expectMarkerNear(page, 48.857, 2.353);

    // A further real update moves the marker (smooth CSS transition class).
    await driverReportLocation(page, driver, ride.id, 48.858, 2.354);
    await expectMarkerNear(page, 48.858, 2.354);
    await expect(marker).toHaveClass(/im-marker-move/);

    // Active ride stays fully functional while tracking.
    await expect(page.getByTestId('cancel-ride')).toBeVisible();
  });

  test('WebSocket unavailability falls back to Redis GEO polling without breaking the ride', async ({ page }) => {
    // Force-close every WS attempt → hook retries with backoff, never "open".
    await page.routeWebSocket(/\/ws\/location/, (ws) => {
      ws.close();
    });

    await bookRideInUi(page);
    const ride = await getAssignedRide(page, customer);

    // With WS unavailable, the REST/GEO polling fallback must surface the fix.
    await driverReportLocation(page, driver, ride.id, 48.8575, 2.3525);
    await expectMarkerNear(page, 48.8575, 2.3525, 30_000);

    // Active ride remains intact: correct state badge + permitted cancel action.
    await expect(
      page.locator('[data-testid="active-ride-card"] .im-badge[data-status="DRIVER_ASSIGNED"]'),
    ).toBeVisible();
    await expect(page.getByTestId('cancel-ride')).toBeVisible();
  });
});


