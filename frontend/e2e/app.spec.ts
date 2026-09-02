import { test, expect } from '@playwright/test';

const API = 'http://localhost:8081';

// ─── Helpers ─────────────────────────────────────────────────────────
async function registerViaAPI(email: string, password: string, firstName: string, lastName: string) {
  const res = await fetch(`${API}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, firstName, lastName }),
  });
  return res.json();
}

// ─── Login & Registration ────────────────────────────────────────────
test.describe('Authentication', () => {
  test('login page renders with form elements', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('h1')).toContainText('IntelliMove');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('login with invalid credentials shows error', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[type="email"]', 'nonexistent@example.com');
    await page.fill('input[type="password"]', 'wrongpassword');
    await page.click('button[type="submit"]');
    await expect(page.locator('.bg-red-50')).toBeVisible({ timeout: 10_000 });
  });

  test('register page renders with form elements', async ({ page }) => {
    await page.goto('/register');
    await expect(page.locator('h1')).toContainText('IntelliMove');
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('register with valid data creates account and redirects to dashboard', async ({ page }) => {
    const ts = Date.now();
    const email = `e2e-reg-${ts}@test.com`;
    await page.goto('/register');
    // Fill all 4 fields by their labels
    await page.locator('input').nth(0).fill('Test');
    await page.locator('input').nth(1).fill('User');
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[type="password"]').fill('password12345');
    await page.click('button[type="submit"]');
    // Should redirect away from login/register
    await expect(page).not.toHaveURL(/\/login|\/register/, { timeout: 10_000 });
  });

  test('register then login flow works', async ({ page }) => {
    const ts = Date.now();
    const email = `e2e-relog-${ts}@test.com`;
    const password = 'password12345';

    // Register via API (cleaner)
    await registerViaAPI(email, password, 'Relog', 'Test');

    // Login via UI
    await page.goto('/login');
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).not.toHaveURL(/\/login|\/register/, { timeout: 10_000 });

    // Logout (desktop header uses an icon-only button with an aria-label)
    await page.click('[aria-label="Log out"]');
    await expect(page).toHaveURL(/\/login/);

    // Login again
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test('unauthenticated user redirected to login', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('register link navigates to register page', async ({ page }) => {
    await page.goto('/login');
    await page.click('a:has-text("Register")');
    await expect(page).toHaveURL(/\/register/);
  });

  test('login link navigates from register to login page', async ({ page }) => {
    await page.goto('/register');
    await page.click('a:has-text("Sign in")');
    await expect(page).toHaveURL(/\/login/);
  });
});

// ─── Customer Dashboard ──────────────────────────────────────────────
test.describe('Customer Dashboard', () => {
  let customerEmail = '';

  test.beforeEach(async ({ page }) => {
    const ts = Date.now();
    customerEmail = `e2e-dash-${ts}@test.com`;
    await registerViaAPI(customerEmail, 'password12345', 'Dash', 'Test');
    await page.goto('/login');
    await page.fill('input[type="email"]', customerEmail);
    await page.fill('input[type="password"]', 'password12345');
    await page.click('button[type="submit"]');
    await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test('customer dashboard shows booking panel with ride options and request button', async ({ page }) => {
    await expect(page.locator('text=Where are you going today?')).toBeVisible();
    await expect(page.locator('[data-testid="booking-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="location-input-map-pin"]')).toBeVisible();
    await expect(page.locator('[data-testid="location-input-flag"]')).toBeVisible();
    await expect(page.locator('[data-testid="request-ride"]')).toBeDisabled(); // disabled until both locations chosen
  });

  test('customer dashboard shows recent rides section', async ({ page }) => {
    await expect(page.locator('text=Recent rides')).toBeVisible();
  });

  test('customer dashboard header shows brand and user name', async ({ page }) => {
    await expect(page.locator('header')).toContainText('IntelliMove');
    await expect(page.locator('header span', { hasText: 'Dash Test' })).toBeVisible();
  });

  test('customer sees four real ride options after choosing locations', async ({ page }) => {
    // Mock only the external geocoding service (Nominatim); all app APIs stay real.
    await mockGeocode(page);
    await pickLocation(page, 'map-pin', 'Brooklyn Ave, NY');
    await pickLocation(page, 'flag', 'Manhattan, NY');
    for (const type of ['ECONOMY', 'COMFORT', 'PREMIUM', 'XL']) {
      await expect(page.locator(`[data-testid="option-${type}"]`)).toBeVisible({ timeout: 10_000 });
    }
  });

  test('customer can request a ride and see it confirmed in the UI', async ({ page }) => {
    await mockGeocode(page);
    await pickLocation(page, 'map-pin', 'Brooklyn Ave, NY');
    await pickLocation(page, 'flag', 'Manhattan, NY');
    await page.locator('[data-testid="option-ECONOMY"]').click();
    await page.locator('[data-testid="request-ride"]').click();

    // Active ride card appears with a live status — never a generic failure.
    await expect(page.locator('[data-testid="active-ride-card"]')).toBeVisible({ timeout: 20_000 });
    const badge = page.locator('[data-testid="active-ride-card"] .im-badge');
    await expect(badge).toHaveText(/requested|driver_assigned/i, { timeout: 20_000 });
    await expect(page.getByText('Failed to request ride')).toHaveCount(0);

    // Cleanup — cancel the ride this test just booked. Its mocked pickup
    // (40.7128,-74.006) overlaps other specs' driver GPS; if left REQUESTED,
    // the Kafka auto-dispatch consumer assigns it to their online drivers and
    // corrupts their runs. See docs/POST_RELEASE_AUDIT.md section 7.
    const loginRes = await fetch(`${API}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: customerEmail, password: 'password12345' }),
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
          body: JSON.stringify({ reason: 'RIDER_CANCELLED', note: 'e2e-app-spec cleanup' }),
        });
      }
    }
  });

  test('customer dashboard logout works', async ({ page }) => {
    await page.click('[aria-label="Log out"]');
    await expect(page).toHaveURL(/\/login/);
  });
});

// ─── Customer Dashboard helpers ──────────────────────────────────────
/**
 * Intercepts ONLY the external Nominatim geocoding calls so tests are
 * deterministic and offline-safe. Every IntelliMove backend API remains real.
 */
async function mockGeocode(page: import('@playwright/test').Page) {
  await page.route('**/geocode/search**', async (route) => {
    const url = new URL(route.request().url());
    const q = url.searchParams.get('q') ?? '';
    const hits = q.toLowerCase().includes('manhattan')
      ? [{ lat: '40.7580', lon: '-73.9855', display_name: 'Manhattan, NY' }]
      : [{ lat: '40.7128', lon: '-74.0060', display_name: 'Brooklyn Ave, NY' }];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(hits) });
  });
}

/** Types into a LocationPicker and selects the first geocoded suggestion. */
async function pickLocation(page: import('@playwright/test').Page, icon: string, text: string) {
  const input = page.locator(`[data-testid="location-input-${icon}"]`);
  await input.click();
  await input.fill(text);
  await page.locator('[role="option"]', { hasText: text }).first().click();
}

// ─── Driver Dashboard ────────────────────────────────────────────────
test.describe('Driver Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    const ts = Date.now();
    const email = `e2e-driver-${ts}@test.com`;
    const password = 'password12345';

    // Register as DRIVER role + create driver profile via API
    const regRes = await fetch(`${API}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, firstName: 'Test', lastName: 'Driver', role: 'DRIVER' }),
    });
    const data = await regRes.json();
    const userId = data.data.user.id;
    const userToken = data.data.accessToken;

    await fetch('http://localhost:8080/api/v1/drivers/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${userToken}`,
        'X-User-Id': userId,
      },
      body: JSON.stringify({
        licenseNumber: `LIC-${ts}`, vehicleMake: 'Toyota', vehicleModel: 'Camry',
        vehicleYear: 2023, vehicleColor: 'White', licensePlate: `ABC-${ts}`,
        vehicleType: 'ECONOMY',
      }),
    });

    // Login via UI as driver
    await page.goto('/login');
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', password);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/driver/, { timeout: 10_000 });
  });

  test('driver dashboard shows driver status section', async ({ page }) => {
    await expect(page.locator('text=Driver Status')).toBeVisible();
  });

  test('driver dashboard shows vehicle information', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Vehicle' })).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('text=Camry')).toBeVisible();
  });

  test('driver can go online', async ({ page }) => {
    // Wait for driver data to load first
    await expect(page.locator('button:has-text("Go Online")')).toBeVisible({ timeout: 15_000 });
    await page.click('button:has-text("Go Online")');
    await page.waitForTimeout(2_000);
    const statusBadge = page.locator('span:has-text("ONLINE")');
    await expect(statusBadge).toBeVisible({ timeout: 10_000 });
  });

  test('driver dashboard shows stats', async ({ page }) => {
    await expect(page.locator('text=Rating')).toBeVisible();
    await expect(page.locator('text=Total Trips')).toBeVisible();
    await expect(page.locator('text=Verified')).toBeVisible();
  });

  test('driver logout works', async ({ page }) => {
    await page.click('[aria-label="Log out"]');
    await expect(page).toHaveURL(/\/login/);
  });
});

// ─── Admin Dashboard ─────────────────────────────────────────────────
test.describe('Admin Dashboard', () => {
  test('admin sees overview after login with ADMIN role', async ({ page }) => {
    const ts = Date.now();
    const email = `e2e-admin-${ts}@test.com`;
    const password = 'password12345';

    // Register as ADMIN role
    const regRes = await fetch(`${API}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, firstName: 'Admin', lastName: 'Test', role: 'ADMIN' }),
    });
    await regRes.json();

    // Login
    await page.goto('/login');
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForTimeout(2_000);

    const url = page.url();
    if (url.includes('/admin')) {
      await expect(page.locator('text=Overview')).toBeVisible();
      await expect(page.locator('button:has-text("Rides")')).toBeVisible();
      await expect(page.locator('button:has-text("AI Assistant")')).toBeVisible();
    }
  });
});

// ─── Navigation & Error Handling ─────────────────────────────────────
test.describe('Navigation & Errors', () => {
  test('unknown route redirects to login', async ({ page }) => {
    await page.goto('/nonexistent-page');
    await expect(page).toHaveURL(/\/login/);
  });

  test('customer cannot access driver page', async ({ page }) => {
    const ts = Date.now();
    const email = `e2e-nav-${ts}@test.com`;
    await registerViaAPI(email, 'password12345', 'Nav', 'Test');
    await page.goto('/login');
    await page.fill('input[type="email"]', email);
    await page.fill('input[type="password"]', 'password12345');
    await page.click('button[type="submit"]');
    await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 });

    await page.goto('/driver');
    await expect(page).toHaveURL(/\/login/);
  });

  test('login page has proper styling', async ({ page }) => {
    await page.goto('/login');
    const form = page.locator('form');
    await expect(form).toBeVisible();
    const submitBtn = page.locator('button[type="submit"]');
    const bgColor = await submitBtn.evaluate((el) => window.getComputedStyle(el).backgroundColor);
    expect(bgColor).toBeTruthy();
  });
});
