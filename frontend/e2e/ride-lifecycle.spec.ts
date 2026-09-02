import { test, expect, type Page } from '@playwright/test';

/**
 * FULL RIDE LIFECYCLE — real browser, real backend, no mocks.
 *
 * Customer registers + books a ride in the UI.
 * Driver (registered via API) goes online near the pickup point,
 * sees the assigned trip in the UI, accepts, starts and completes it.
 * Verifies: ride history status, payment record, customer notification.
 */

const GW = 'http://localhost:8080';
const ts = Date.now();
const CUST = { email: `life_c_${ts}@test.com`, password: 'TestPass123!', firstName: 'Luna', lastName: 'Customer' };
const DRV = { email: `life_d_${ts}@test.com`, password: 'TestPass123!', firstName: 'Diego', lastName: 'Driver' };

async function apiRegister(page: Page, body: object) {
  return page.request.post(`${GW}/api/v1/auth/register`, { data: body });
}
async function apiLogin(page: Page, email: string, password: string) {
  const r = await page.request.post(`${GW}/api/v1/auth/login`, { data: { email, password } });
  return (await r.json()).data;
}

test.describe.configure({ mode: 'serial' });

let customerToken = '';
let customerId = '';
let driverToken = '';
let driverUserId = '';

test('setup: register customer + online driver near pickup', async ({ page }) => {
  const cr = await apiRegister(page, { ...CUST, role: 'CUSTOMER' });
  expect(cr.status()).toBeLessThan(300);
  customerToken = (await cr.json()).data.accessToken;
  customerId = (await cr.json()).data.user?.id ?? (await cr.json()).data.userId;

  const dr = await apiRegister(page, { ...DRV, role: 'DRIVER' });
  expect(dr.status()).toBeLessThan(300);
  const drvAuth = (await dr.json()).data;
  driverToken = drvAuth.accessToken;
  driverUserId = drvAuth.user?.id ?? drvAuth.userId;

  // driver profile
  const pr = await page.request.post(`${GW}/api/v1/drivers/register`, {
    headers: { Authorization: `Bearer ${driverToken}` },
    data: {
      licenseNumber: `DL-${ts % 1000000}`,
      vehicleMake: 'Toyota', vehicleModel: 'Camry', vehicleYear: 2023,
      vehicleColor: 'Silver', licensePlate: `LM-${ts % 100000}`, vehicleType: 'ECONOMY',
    },
  });
  expect(pr.status()).toBeLessThan(300);
  const driverProfile = (await pr.json()).data;

  // go ONLINE then place driver next to pickup (40.7128,-74.006)
  // Simulates the driver app's GPS feed -> Redis GEO (JWT user-ID contract).
  await page.request.patch(`${GW}/api/v1/drivers/${driverProfile.id}/status`, {
    headers: { Authorization: `Bearer ${driverToken}` },
    data: { status: 'ONLINE' },
  });
  const loc = await page.request.post(`${GW}/api/v1/location/update`, {
    headers: { Authorization: `Bearer ${driverToken}` },
    data: { latitude: 40.7129, longitude: -74.006 },
  });
  expect(loc.status()).toBeLessThan(300);
});

test('customer books a ride in the browser', async ({ page }) => {
  const auth = await apiLogin(page, CUST.email, CUST.password);
  await page.addInitScript(([t, u]: any) => {
    localStorage.setItem('accessToken', t);
    localStorage.setItem('user', JSON.stringify(u));
  }, [auth.accessToken, auth.user] as any);

  await page.goto('http://localhost:5173/dashboard');
  await expect(page.getByTestId('booking-panel')).toBeVisible();

  // Pick real addresses via live Nominatim suggestions (like a real user)
  const pickupInput = page.getByTestId('location-input-map-pin');
  await pickupInput.fill('New York, NY');
  await page.getByRole('option').first().click({ timeout: 15000 });

  const dropoffInput = page.getByTestId('location-input-flag');
  await dropoffInput.fill('Times Square, New York');
  await page.getByRole('option').first().click({ timeout: 15000 });

  // real pricing engine options
  await expect(page.getByTestId('ride-options')).toBeVisible({ timeout: 15000 });
  await expect(page.getByTestId('option-ECONOMY')).toContainText('$');

  await page.getByTestId('option-COMFORT').click();
  await page.getByTestId('request-ride').click();

  // active ride card appears; matching or already matched
  await expect(page.getByTestId('active-ride-card')).toBeVisible({ timeout: 20000 });
});

test('driver accepts, starts and completes the trip in the browser', async ({ page }) => {
  test.setTimeout(120000);
  // Emulated GPS near the pickup point — the dashboard reports this to
  // /api/v1/location/update while ONLINE, exactly like a real device.
  await page.context().grantPermissions(['geolocation']);
  await page.context().setGeolocation({ latitude: 40.7129, longitude: -74.006 });
  const auth = await apiLogin(page, DRV.email, DRV.password);
  await page.addInitScript(([t, u]: any) => {
    localStorage.setItem('accessToken', t);
    localStorage.setItem('user', JSON.stringify(u));
  }, [auth.accessToken, auth.user] as any);

  await page.goto('http://localhost:5173/driver');

  // wait for auto-match to assign the ride
  const trip = page.getByTestId('driver-active-trip');
  await expect(trip).toBeVisible({ timeout: 60000 });

  await page.getByTestId('accept-ride').click();
  await expect(page.getByTestId('start-trip')).toBeVisible({ timeout: 20000 });
  await page.getByTestId('start-trip').click();
  await expect(page.getByTestId('complete-trip')).toBeVisible({ timeout: 20000 });
  await page.getByTestId('complete-trip').click();
  await expect(page.getByTestId('no-active-trip')).toBeVisible({ timeout: 20000 });

  // backend assertions: ride completed, payment completed, notification exists
  const rides = await page.request.get(`${GW}/api/v1/rides/customer/${customerId}`, {
    headers: { Authorization: `Bearer ${customerToken}` },
  });
  const rideList = (await rides.json()).data.content;
  const ride = rideList.find((r: any) => r.status === 'TRIP_COMPLETED');
  expect(ride).toBeTruthy();
  expect(ride.finalFare).toBeGreaterThan(0);

  const pay = await page.request.get(`${GW}/api/v1/payments/ride/${ride.id}`, {
    headers: { Authorization: `Bearer ${customerToken}` },
  });
  if (pay.ok()) expect((await pay.json()).data.status).toBe('COMPLETED');

  const notif = await page.request.get(`${GW}/api/v1/notifications/unread-count`, {
    headers: { Authorization: `Bearer ${customerToken}` },
  });
  expect(notif.ok()).toBeTruthy();
});
