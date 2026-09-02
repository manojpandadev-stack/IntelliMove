import { test, expect, type Page } from '@playwright/test';

/**
 * DRIVER RIDE REQUEST & ACCEPTANCE — real browser, real IntelliMove stack.
 *
 * Customer registers + books a ride via the real API → Redis GEO matching
 * assigns an ONLINE driver → the driver dashboard shows the Uber-style
 * incoming request card with ONLY backend-provided data → accept/reject via
 * the existing ride lifecycle endpoints.
 *
 * No ride data is mocked.
 */

const GW = 'http://localhost:8080';
const PICKUP = { latitude: 40.7128, longitude: -74.006 }; // same spot as ride-lifecycle spec
const DROPOFF = { latitude: 40.758, longitude: -73.9851 };

interface Auth {
  token: string;
  userId: string;
}

async function register(page: Page, body: Record<string, unknown>): Promise<Auth> {
  const r = await page.request.post(`${GW}/api/v1/auth/register`, { data: body });
  expect(r.status()).toBeLessThan(300);
  const data = (await r.json()).data;
  return { token: data.accessToken, userId: data.user?.id ?? data.userId };
}

async function registerDriver(
  page: Page,
  creds: { email: string; password: string; firstName: string; lastName: string },
  ts: number,
): Promise<Auth> {
  const auth = await register(page, { ...creds, role: 'DRIVER' });
  const pr = await page.request.post(`${GW}/api/v1/drivers/register`, {
    headers: { Authorization: `Bearer ${auth.token}` },
    data: {
      licenseNumber: `DL-${ts % 1000000}-${creds.firstName}`,
      vehicleMake: 'Toyota', vehicleModel: 'Camry', vehicleYear: 2023,
      vehicleColor: 'Silver', licensePlate: `LM-${ts % 100000}-${creds.firstName.charAt(0)}`,
      vehicleType: 'ECONOMY',
    },
  });
  expect(pr.status()).toBeLessThan(300);
  const profile = (await pr.json()).data;
  // Driver ONLINE (existing product rule for matching eligibility).
  const st = await page.request.patch(`${GW}/api/v1/drivers/${profile.id}/status`, {
    headers: { Authorization: `Bearer ${auth.token}` },
    data: { status: 'ONLINE' },
  });
  expect(st.status()).toBeLessThan(300);
  return auth;
}

async function goOnlineNear(page: Page, auth: Auth) {
  // Redis GEO heartbeat (JWT user-ID contract) right next to the pickup point.
  const loc = await page.request.post(`${GW}/api/v1/location/update`, {
    headers: { Authorization: `Bearer ${auth.token}` },
    data: { latitude: PICKUP.latitude + 0.0001, longitude: PICKUP.longitude },
  });
  expect(loc.status()).toBeLessThan(300);
}

async function bookRide(page: Page, customer: Auth, rideType: string): Promise<string> {
  const r = await page.request.post(`${GW}/api/v1/rides`, {
    headers: { Authorization: `Bearer ${customer.token}` },
    data: {
      rideType,
      ...PICKUP,
      ...DROPOFF,
      pickupAddress: 'Bowling Green, New York',
      dropoffAddress: 'Times Square, New York',
    },
  });
  expect(r.status()).toBeLessThan(300);
  const ride = (await r.json()).data;
  expect(ride.status).toBe('REQUESTED');
  return ride.id as string;
}

async function cancelAllActiveRides(page: Page, customer: Auth) {
  const ridesRes = await page.request.get(`${GW}/api/v1/rides/customer/${customer.userId}`, {
    headers: { Authorization: `Bearer ${customer.token}` },
  });
  const rides = (await ridesRes.json()).data?.content ?? [];
  for (const ride of rides) {
    if (['REQUESTED', 'DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING', 'TRIP_STARTED'].includes(ride.status)) {
      await page.request.post(`${GW}/api/v1/rides/${ride.id}/cancel`, {
        headers: { Authorization: `Bearer ${customer.token}`, 'Content-Type': 'application/json' },
        data: { reason: 'RIDER_CANCELLED', note: 'driver-request spec cleanup' },
      });
    }
  }
}

async function loginAsDriver(page: Page, auth: Auth) {
  // Real GPS so the dashboard heartbeats the driver's live position like a device.
  await page.context().grantPermissions(['geolocation']);
  await page.context().setGeolocation({ latitude: PICKUP.latitude + 0.0001, longitude: PICKUP.longitude });
  await page.addInitScript(([t, u]: any) => {
    localStorage.setItem('accessToken', t);
    localStorage.setItem('user', JSON.stringify(u));
  }, [auth.token, { id: auth.userId, role: 'DRIVER' }] as any);
  await page.goto('http://localhost:5173/driver');
}

test.describe.configure({ mode: 'serial' });

let ts: number;
let customer: Auth;
let driverA: Auth;
let driverB: Auth;

test.describe('Driver ride request & acceptance', () => {

  test('setup: register customer + two online drivers near pickup', async ({ page }) => {
    ts = Date.now();
    customer = await register(page, {
      email: `dreq_c_${ts}@test.com`, password: 'TestPass123!', firstName: 'Cara', lastName: 'Rider', role: 'CUSTOMER',
    });
    driverA = await registerDriver(page, {
      email: `dreq_da_${ts}@test.com`, password: 'TestPass123!', firstName: 'Adam', lastName: 'DriverA',
    }, ts);
    driverB = await registerDriver(page, {
      email: `dreq_db_${ts}@test.com`, password: 'TestPass123!', firstName: 'Bella', lastName: 'DriverB',
    }, ts);
    await goOnlineNear(page, driverA);
    await goOnlineNear(page, driverB);
  });

  test('driver sees real incoming request, accepts, starts and completes the trip', async ({ page }) => {
    test.setTimeout(180_000);
    // Driver dashboard open BEFORE booking — proves real-time delivery.
    await loginAsDriver(page, driverA);
    await expect(page.getByTestId('driver-status-badge')).toHaveText(/ONLINE/, { timeout: 20_000 });
    await expect(page.getByTestId('no-active-trip')).toBeVisible({ timeout: 20_000 });

    const rideId = await bookRide(page, customer, 'COMFORT');

    // Incoming request card appears without a page reload (WS push or REST poll).
    const card = page.getByTestId('driver-request-card');
    await expect(card).toBeVisible({ timeout: 60_000 });

    // Exactly ONE request card even with WS + polling refreshes combined.
    await expect(page.getByTestId('driver-request-card')).toHaveCount(1);

    // Only backend-provided data is displayed.
    await expect(card).toContainText('COMFORT');
    await expect(card).toContainText('Bowling Green, New York');
    await expect(card).toContainText('Times Square, New York');
    await expect(card.getByText('Cara Rider')).toBeVisible(); // real customer info from the user service
    const fare = card.locator('dd', { hasText: 'USD' }).first();
    await expect(fare).toContainText(/\d/); // real pricing-engine estimate

    // Countdown is visible (client-side visual only).
    await expect(card.getByTestId('request-countdown')).toBeVisible();

    // Duplicate assignment events must not duplicate the card.
    await page.waitForTimeout(11_000); // > two 5s REST poll cycles
    await expect(page.getByTestId('driver-request-card')).toHaveCount(1);

    // Accept via the existing ride lifecycle endpoint.
    await page.getByTestId('accept-ride').click();
    const trip = page.getByTestId('driver-active-trip');
    await expect(trip).toBeVisible({ timeout: 20_000 });
    await expect(trip.locator('.im-badge')).toHaveText(/DRIVER_ACCEPTED/i, { timeout: 20_000 });
    await expect(trip.getByTestId('trip-customer-name')).toContainText('Cara Rider');
    await expect(trip.getByTestId('navigate-action')).toBeVisible();

    // Customer sees DRIVER_ACCEPTED through the existing ride-status flow.
    const rideRes = await page.request.get(`${GW}/api/v1/rides/${rideId}`, {
      headers: { Authorization: `Bearer ${customer.token}` },
    });
    expect((await rideRes.json()).data.status).toBe('DRIVER_ACCEPTED');

    // Existing start / complete flow still works.
    await page.getByTestId('start-trip').click();
    await expect(page.getByTestId('complete-trip')).toBeVisible({ timeout: 20_000 });
    await page.getByTestId('complete-trip').click();
    await expect(page.getByTestId('no-active-trip')).toBeVisible({ timeout: 20_000 });
  });

  test('second driver never sees or can accept another driver\'s assigned request', async ({ page }) => {
    test.setTimeout(120_000);
    const rideId = await bookRide(page, customer, 'ECONOMY');

    await loginAsDriver(page, driverB);
    const card = page.getByTestId('driver-request-card');
    // If driver B was matched (possible when A is free), only then can B accept.
    await expect(card.or(page.getByTestId('no-active-trip'))).toBeVisible({ timeout: 60_000 });

    if (await card.isVisible()) {
      // B was legitimately assigned this ride — accept through the UI.
      await page.getByTestId('accept-ride').click();
    } else {
      // B was NOT assigned: direct API acceptance must be rejected.
      const res = await page.request.post(`${GW}/api/v1/rides/${rideId}/accept`, {
        headers: { Authorization: `Bearer ${driverB.token}` },
      });
      expect(res.status()).toBeGreaterThanOrEqual(400);
    }

    // Security: unauthenticated caller -> 401, customer -> 403 on driver endpoint.
    const unauth = await page.request.post(`${GW}/api/v1/rides/${rideId}/accept`);
    expect(unauth.status()).toBe(401);
    const custAttempt = await page.request.post(`${GW}/api/v1/rides/${rideId}/accept`, {
      headers: { Authorization: `Bearer ${customer.token}` },
    });
    expect(custAttempt.status()).toBe(403);
  });

  test('driver can reject; ride is reassignable, NOT cancelled', async ({ page }) => {
    test.setTimeout(180_000);
    const rideId = await bookRide(page, customer, 'ECONOMY');

    await loginAsDriver(page, driverA);
    const card = page.getByTestId('driver-request-card');
    await expect(card).toBeVisible({ timeout: 60_000 });

    await page.getByTestId('reject-ride').click();
    // The request leaves the driver's dashboard (no request card, no active trip).
    await expect(page.getByTestId('no-active-trip')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('driver-request-card')).toHaveCount(0);

    // Backend: ride must be REQUESTED (waiting for reassignment) or already
    // re-assigned — never CANCELLED/COMPLETED by a rejection.
    let status = '';
    for (let i = 0; i < 12; i++) {
      const res = await page.request.get(`${GW}/api/v1/rides/${rideId}`, {
        headers: { Authorization: `Bearer ${customer.token}` },
      });
      status = (await res.json()).data.status;
      if (status !== 'DRIVER_ASSIGNED') break;
      await page.waitForTimeout(2_000);
    }
    expect(['REQUESTED', 'DRIVER_ASSIGNED']).toContain(status);
  });

  test('expired countdown disables acceptance (client-side, no fake backend expiry)', async ({ page }) => {
    test.setTimeout(120_000);
    const rideId = await bookRide(page, customer, 'ECONOMY');

    await page.context().grantPermissions(['geolocation']);
    await page.context().setGeolocation({ latitude: PICKUP.latitude + 0.0001, longitude: PICKUP.longitude });
    // Freeze and control client time — the countdown is pure client behaviour.
    await page.clock.install();
    await page.addInitScript(([t, u]: any) => {
      localStorage.setItem('accessToken', t);
      localStorage.setItem('user', JSON.stringify(u));
    }, [driverA.token, { id: driverA.userId, role: 'DRIVER' }] as any);
    await page.goto('http://localhost:5173/driver');

    const card = page.getByTestId('driver-request-card');
    await expect(card).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('request-countdown')).toBeVisible();

    // Fast-forward past the acceptance window.
    await page.clock.fastForward(60_000);

    await expect(page.getByTestId('request-expired')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('accept-ride')).toHaveCount(0); // accept UI removed once expired
    // Backend state machine remains the authority: ride untouched by expiry.
    const res = await page.request.get(`${GW}/api/v1/rides/${rideId}`, {
      headers: { Authorization: `Bearer ${customer.token}` },
    });
    const status = (await res.json()).data.status;
    expect(['REQUESTED', 'DRIVER_ASSIGNED']).toContain(status);
  });

  test('mobile responsive layout with 48px accessible touch targets', async ({ page }) => {
    test.setTimeout(120_000);
    await bookRide(page, customer, 'ECONOMY');

    await page.setViewportSize({ width: 390, height: 844 }); // phone viewport
    await loginAsDriver(page, driverA);
    const card = page.getByTestId('driver-request-card');
    await expect(card).toBeVisible({ timeout: 60_000 });

    const accept = page.getByTestId('accept-ride');
    await expect(accept).toBeVisible();
    expect((await accept.boundingBox())?.height).toBeGreaterThanOrEqual(48);

    const reject = page.getByTestId('reject-ride');
    await expect(reject).toBeVisible();
    expect((await reject.boundingBox())?.height).toBeGreaterThanOrEqual(48);

    // Keyboard accessible: Accept is a real <button> reachable by keyboard.
    await accept.focus();
    await expect(accept).toBeFocused();

    // No horizontal overflow on a phone viewport (Dark Rose responsive card).
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 2,
    );
    expect(overflow).toBe(false);
  });

  test('cleanup: cancel leftover rides for the test customer', async ({ page }) => {
    await cancelAllActiveRides(page, customer);
  });
});

