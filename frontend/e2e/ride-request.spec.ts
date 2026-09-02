import { test, expect, type Page } from '@playwright/test';

const API = 'http://localhost:8081';

async function registerCustomer(email: string, password: string) {
  const res = await fetch(`${API}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, firstName: 'Reg', lastName: 'Test', role: 'CUSTOMER' }),
  });
  return res.json();
}

/**
 * Intercepts ONLY the third-party Nominatim geocoding service so the test is
 * deterministic and offline-safe. All IntelliMove backend APIs remain real:
 * auth, gateway, ride service, pricing engine, Redis GEO matching.
 */
async function mockGeocode(page: Page) {
  await page.route('**/geocode/search**', async (route) => {
    const url = new URL(route.request().url());
    const q = url.searchParams.get('q') ?? '';
    const hits = q.toLowerCase().includes('manhattan')
      ? [{ lat: '40.7580', lon: '-73.9855', display_name: 'Manhattan, NY' }]
      : [{ lat: '40.7128', lon: '-74.0060', display_name: 'Brooklyn Ave, NY' }];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(hits) });
  });
}

test.describe('Customer ride request (end-to-end)', () => {
  test('customer can successfully request a ride and it appears in history', async ({ page }) => {
    test.setTimeout(90_000);
    const ts = Date.now();
    const email = `ridereq-${ts}@test.com`;

    await registerCustomer(email, 'password12345');

    // 1. Login via UI
    await page.goto('/login');
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', 'password12345');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });

    // 2. Choose pickup + destination through the real LocationPicker flow
    await mockGeocode(page);

    const pickupInput = page.locator('[data-testid="location-input-map-pin"]');
    await pickupInput.click();
    await pickupInput.fill('Brooklyn Ave, NY');
    await page.locator('[role="option"]', { hasText: 'Brooklyn Ave, NY' }).first().click();

    const dropoffInput = page.locator('[data-testid="location-input-flag"]');
    await dropoffInput.click();
    await dropoffInput.fill('Manhattan, NY');
    await page.locator('[role="option"]', { hasText: 'Manhattan, NY' }).first().click();

    // 3. Fare estimates arrive from the live pricing engine; pick PREMIUM
    await expect(page.locator('[data-testid="option-PREMIUM"]')).toBeVisible({ timeout: 15_000 });
    await page.locator('[data-testid="option-PREMIUM"]').click();

    // 4. Request the ride
    await page.locator('[data-testid="request-ride"]').click();

    // 5. Active ride card appears with a live status — no generic failure text
    const activeCard = page.locator('[data-testid="active-ride-card"]');
    await expect(activeCard).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('Failed to request ride')).toHaveCount(0);
    await expect(page.getByText('Unable to request')).toHaveCount(0);

    // 6. Ride starts as REQUESTED (or already matched by Redis GEO matching)
    await expect(activeCard.locator('.im-badge')).toHaveText(/requested|driver_assigned/i, {
      timeout: 20_000,
    });

    // 7. The ride appears in My Rides with a valid initial status
    await page.click('a:has-text("My Rides")');
    await expect(page).toHaveURL(/\/rides/, { timeout: 10_000 });
    await expect(page.locator('text=Brooklyn Ave, NY').first()).toBeVisible({ timeout: 15_000 });
    await expect(
      page
        .locator('.im-badge[data-status="REQUESTED"], .im-badge[data-status="DRIVER_ASSIGNED"]')
        .first()
    ).toBeVisible();

    // 8. Cleanup — cancel this spec's still-active rides. Without this, the
    // Kafka auto-dispatch consumer keeps retrying them and assigns them to any
    // ONLINE driver near the pickup point (which overlaps other specs' test
    // GPS), corrupting their runs. See docs/POST_RELEASE_AUDIT.md section 7.
    const loginRes = await fetch(`${API}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: 'password12345' }),
    });
    const loginData = (await loginRes.json()).data;
    const token = loginData.accessToken as string;
    const customerId = (loginData.user?.id ?? loginData.userId) as string;
    const ridesRes = await fetch(`http://localhost:8080/api/v1/rides/customer/${customerId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const rides = (await ridesRes.json()).data?.content ?? [];
    for (const ride of rides) {
      if (
        ['REQUESTED', 'DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING', 'IN_PROGRESS']
          .includes(ride.status)
      ) {
        await fetch(`http://localhost:8080/api/v1/rides/${ride.id}/cancel`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({ reason: 'RIDER_CANCELLED', note: 'e2e-ride-request cleanup' }),
        });
      }
    }
  });
});

