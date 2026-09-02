import { test, expect, type Page } from '@playwright/test';

/**
 * RESPONSIVE RIDER EXPERIENCE
 *
 * Verifies the rider dashboard adapts across desktop / tablet / mobile:
 * navigation patterns, booking flow, fare estimates, active ride,
 * notification popover, profile menu, floating action, and dark mode.
 *
 * Only the external Nominatim geocoding service is mocked — every
 * IntelliMove backend API stays real.
 */

const API = 'http://localhost:8081';

async function registerAndLogin(page: Page, tag: string) {
  const ts = Date.now();
  const email = `${tag}-${ts}@test.com`;
  await fetch(`${API}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'password12345', firstName: 'Resp', lastName: 'Test' }),
  });
  await page.goto('/login');
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', 'password12345');
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  return email;
}

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

/** Cancels this spec's still-active rides so Kafka auto-dispatch cannot
 * assign them to other specs' online drivers (see ride-request.spec). */
async function cancelActiveRides(email: string) {
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
    if (['REQUESTED', 'DRIVER_ASSIGNED', 'DRIVER_ACCEPTED', 'DRIVER_ARRIVING'].includes(ride.status)) {
      await fetch(`http://localhost:8080/api/v1/rides/${ride.id}/cancel`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ reason: 'RIDER_CANCELLED', note: 'responsive-spec cleanup' }),
      });
    }
  }
}

// ─── Desktop ─────────────────────────────────────────────────────────
test.describe('Responsive rider — desktop', () => {
  test.use({ viewport: { width: 1366, height: 850 } });

  test('desktop header, navigation, dashboard sections and popovers', async ({ page }) => {
    await registerAndLogin(page, 'resp_desktop');

    // Header: brand, user name, notification bell, profile menu, logout
    await expect(page.locator('header')).toContainText('IntelliMove');
    await expect(page.getByTestId('header-user-name')).toBeVisible();
    const bellButton = page.locator('header button[data-testid="notifications-bell"]');
    await expect(bellButton).toBeVisible();
    await expect(page.locator('[aria-label="Log out"]')).toBeVisible();

    // Desktop primary navigation is visible (no hamburger)
    await expect(page.locator('header nav[aria-label="Primary"] a', { hasText: 'My Rides' })).toBeVisible();
    await expect(page.getByLabel('Toggle navigation menu')).toBeHidden();

    // Notification popover: opens with real content area and closes on Escape
    await bellButton.click();
    const dialog = page.locator('[role="dialog"][aria-label="Notifications"]');
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();

    // Profile dropdown shows account name + links
    await page.getByTestId('profile-menu').click();
    await expect(page.getByTestId('profile-menu-name')).toContainText('Resp Test');
    await expect(page.getByRole('menuitem', { name: 'My profile' })).toBeVisible();
    await page.keyboard.press('Escape');

    // Dashboard sections
    await expect(page.getByTestId('booking-panel')).toBeVisible();
    await expect(page.locator('section[aria-label="Quick actions"]')).toBeVisible();
    await expect(page.locator('section[aria-label="Saved places"]')).toBeVisible();
    await expect(page.getByTestId('recent-rides')).toBeVisible();
    await expect(page.locator('section[aria-label="Help and safety"]')).toBeVisible();

    // Map controls exist in the hero map
    await expect(page.getByRole('button', { name: 'Zoom in' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reset route view' })).toBeVisible();

    // Floating action is a mobile-only affordance; no bottom nav on desktop
    await expect(page.getByTestId('fab-book')).toBeHidden();
    await expect(page.locator('nav[aria-label="Bottom navigation"]')).toBeHidden();
  });

  test('fare estimate appears after choosing pickup and destination', async ({ page }) => {
    await registerAndLogin(page, 'resp_fare');
    await mockGeocode(page);
    const pickup = page.getByTestId('location-input-map-pin');
    await pickup.click();
    await pickup.fill('Brooklyn Ave, NY');
    await page.locator('[role="option"]', { hasText: 'Brooklyn Ave, NY' }).first().click();
    const dropoff = page.getByTestId('location-input-flag');
    await dropoff.click();
    await dropoff.fill('Manhattan, NY');
    await page.locator('[role="option"]', { hasText: 'Manhattan, NY' }).first().click();

    // Real pricing engine returns all four ride types with fares + ETAs
    for (const type of ['ECONOMY', 'COMFORT', 'PREMIUM', 'XL']) {
      await expect(page.getByTestId(`option-${type}`).getByText('$')).toBeVisible({ timeout: 15_000 });
    }
    await expect(page.locator('[data-testid="ride-options"]').getByText('km trip')).toBeVisible();
  });
});

// ─── Tablet ──────────────────────────────────────────────────────────
test.describe('Responsive rider — tablet', () => {
  test.use({ viewport: { width: 834, height: 1112 } });

  test('tablet uses condensed header without bottom nav; booking stays usable', async ({ page }) => {
    await registerAndLogin(page, 'resp_tablet');

    // Condensed desktop-style header (hamburger hidden at md+)
    await expect(page.getByLabel('Toggle navigation menu')).toBeHidden();
    await expect(page.locator('nav[aria-label="Bottom navigation"]')).toBeHidden();
    await expect(page.locator('header')).toContainText('IntelliMove');

    // Full-width adaptive layout keeps the whole booking experience usable
    await expect(page.getByTestId('booking-panel')).toBeVisible();
    await expect(page.getByTestId('location-input-map-pin')).toBeVisible();
    await expect(page.getByTestId('location-input-flag')).toBeVisible();
    await expect(page.getByTestId('request-ride')).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Swap pickup and destination' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Use my current location as pickup' })).toBeVisible();
  });
});

// ─── Mobile ──────────────────────────────────────────────────────────
test.describe('Responsive rider — mobile', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('app-like header, bottom navigation and floating action', async ({ page }) => {
    await registerAndLogin(page, 'resp_mobile_nav');

    // Compact header: hamburger, brand, bell (→ dedicated page), profile icon
    await expect(page.getByLabel('Toggle navigation menu')).toBeVisible();
    await expect(page.locator('header a[data-testid="notifications-bell"]')).toBeVisible();
    await expect(page.getByTestId('mobile-profile')).toBeVisible();
    await expect(page.getByTestId('header-user-name')).toBeHidden(); // compact

    // Bottom navigation tabs with icons + labels
    const bottomNav = page.locator('nav[aria-label="Bottom navigation"]');
    await expect(bottomNav).toBeVisible();
    for (const label of ['Book', 'Rides', 'Payments', 'Saved', 'Profile']) {
      await expect(bottomNav.getByText(label, { exact: true })).toBeVisible();
    }

    // App-like booking hero
    await expect(page.getByText('Where are you going today?')).toBeVisible();
    await expect(page.getByTestId('booking-panel')).toBeVisible();

    // Floating "Book a ride" action with a comfortable touch target
    const fab = page.getByTestId('fab-book');
    await expect(fab).toBeVisible();
    const box = await fab.boundingBox();
    expect(box && box.height >= 44).toBeTruthy();

    // FAB scrolls to the booking panel
    await fab.click();
    await expect(page.getByTestId('location-input-flag')).toBeInViewport({ timeout: 5_000 });

    // Hamburger drawer still offers full navigation
    await page.getByLabel('Toggle navigation menu').click();
    await expect(page.locator('nav[aria-label="Mobile"]')).toBeVisible();
  });

  test('mobile booking flow: estimate, request ride, live trip card, cleanup', async ({ page }) => {
    test.setTimeout(90_000);
    const email = await registerAndLogin(page, 'resp_mobile_ride');
    await mockGeocode(page);

    const pickup = page.getByTestId('location-input-map-pin');
    await pickup.scrollIntoViewIfNeeded();
    await pickup.click();
    await pickup.fill('Brooklyn Ave, NY');
    await page.locator('[role="option"]', { hasText: 'Brooklyn Ave, NY' }).first().click();
    const dropoff = page.getByTestId('location-input-flag');
    await dropoff.click();
    await dropoff.fill('Manhattan, NY');
    await page.locator('[role="option"]', { hasText: 'Manhattan, NY' }).first().click();

    await expect(page.getByTestId('option-PREMIUM')).toBeVisible({ timeout: 15_000 });
    await page.getByTestId('option-PREMIUM').click();
    await page.getByTestId('request-ride').click();

    // Live trip panel replaces the booking form
    const activeCard = page.getByTestId('active-ride-card');
    await expect(activeCard).toBeVisible({ timeout: 20_000 });
    await expect(activeCard.locator('.im-badge')).toHaveText(/requested|driver_assigned/i, { timeout: 20_000 });

    await cancelActiveRides(email);
  });
});

// ─── Dark mode (mobile) ──────────────────────────────────────────────
test.describe('Responsive rider — dark mode', () => {
  test.use({ viewport: { width: 390, height: 844 }, colorScheme: 'dark' });

  test('inputs stay readable under OS dark scheme', async ({ page }) => {
    await page.goto('/login');
    const email = page.locator('input[type="email"]');
    await email.fill('dark@example.com');
    const style = await email.evaluate((el) => {
      const cs = getComputedStyle(el);
      return { color: cs.color, bg: cs.backgroundColor, caret: cs.caretColor };
    });
    expect(style.color).toBe('rgb(255, 241, 245)'); // #FFF1F5 on #1A0D14
    expect(style.bg).toBe('rgb(26, 13, 20)');       // Dark Rose input surface
    expect(style.caret).toBe('rgb(255, 241, 245)');
  });
});

