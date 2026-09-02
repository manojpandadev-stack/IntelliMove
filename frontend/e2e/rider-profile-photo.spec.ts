import { test, expect, type Page } from '@playwright/test';

/**
 * RIDER PROFILE PHOTO
 *
 * Covers: default avatar, upload (preview → save), persistence across a full
 * page reload (server-side storage), invalid file rejection, and photo
 * removal. Uses the real User Service endpoints through the gateway.
 */

const GW = 'http://localhost:8080';
const PASSWORD = 'TestPass123!';

/** Minimal valid 1x1 PNG (real magic bytes — passes server signature check). */
const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

let counter = 0;

async function registerAndLogin(page: Page): Promise<{ userId: string; token: string }> {
  counter += 1;
  const email = `photo_${Date.now()}_${counter}@test.com`;
  const res = await page.request.post(`${GW}/api/v1/auth/register`, {
    data: { email, password: PASSWORD, firstName: 'Rita', lastName: 'Photo', role: 'CUSTOMER' },
  });
  expect(res.status()).toBeLessThan(300);
  const data = (await res.json()).data;
  const creds = { userId: data.user.id as string, token: data.accessToken as string };

  await page.goto('/login');
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', PASSWORD);
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 });
  return creds;
}

test.describe('Rider profile photo', () => {
  test('default avatar (initials) shown when no photo exists', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/profile');

    // Fallback initials avatar on the profile card and in the desktop header.
    await expect(page.getByTestId('profile-avatar')).toBeVisible();
    const fallback = page.getByTestId('avatar-photo-fallback');
    await expect(fallback).toBeVisible();
    await expect(fallback).toContainText('R');
    // Desktop header trigger (the mobile one is display:none at this width)
    const headerFallback = page
      .getByRole('button', { name: 'Open profile menu' })
      .getByTestId('header-avatar-fallback');
    await expect(headerFallback).toBeVisible();
    // No uploaded photo controls beyond "Upload photo"
    await expect(page.getByTestId('upload-photo-btn')).toBeVisible();
    await expect(page.getByTestId('remove-photo-btn')).toBeHidden();
  });

  test('upload with preview succeeds and persists after reload', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/profile');

    // Choose a real PNG → local preview appears before saving
    await page.getByTestId('photo-input').setInputFiles({
      name: 'me.png', mimeType: 'image/png', buffer: PNG_1X1,
    });
    await expect(page.getByTestId('photo-preview')).toBeVisible();
    await expect(page.getByTestId('save-photo-btn')).toBeVisible();

    // Save → success message → stored photo rendered as an <img>
    await page.getByTestId('save-photo-btn').click();
    await expect(page.getByTestId('photo-success')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('avatar-photo')).toBeVisible();
    await expect(page.getByTestId('change-photo-btn')).toBeVisible();

    // Header reflects the stored photo too (desktop instance of the avatar)
    await expect(
      page.getByRole('button', { name: 'Open profile menu' }).getByTestId('header-avatar'),
    ).toBeVisible();

    // Full reload → photo still there (server-side persistence, not localStorage)
    await page.reload();
    await expect(page.getByTestId('profile-avatar')).toBeVisible();
    await expect(page.locator('[data-testid="avatar-photo"]').first()).toBeVisible();
    await expect(page.getByTestId('remove-photo-btn')).toBeVisible();
  });

  test('invalid files are rejected safely with a readable message', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/profile');

    // A plain .txt is rejected instantly by the client-side type gate.
    await page.getByTestId('photo-input').setInputFiles({
      name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('hello'),
    });
    await expect(page.getByTestId('photo-error')).toBeVisible();
    await expect(page.getByTestId('save-photo-btn')).toBeHidden();

    // Text bytes disguised as .png pass the browser MIME gate but MUST be
    // rejected by the server's magic-byte signature validation.
    await page.getByTestId('photo-input').setInputFiles({
      name: 'fake.png', mimeType: 'image/png', buffer: Buffer.from('this is not an image at all'),
    });
    await expect(page.getByTestId('photo-preview')).toBeVisible();
    await page.getByTestId('save-photo-btn').click();
    await expect(page.getByTestId('photo-error')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('avatar-photo')).toBeHidden();
  });

  test('uploaded photo can be removed, returning to the default avatar', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/profile');

    await page.getByTestId('photo-input').setInputFiles({
      name: 'me.png', mimeType: 'image/png', buffer: PNG_1X1,
    });
    await page.getByTestId('save-photo-btn').click();
    await expect(page.getByTestId('avatar-photo')).toBeVisible({ timeout: 10_000 });

    // Remove → back to initials
    await page.getByTestId('remove-photo-btn').click();
    await expect(page.getByTestId('photo-success')).toContainText('removed');
    await expect(page.getByTestId('avatar-photo-fallback')).toBeVisible();

    // Removal persists after reload
    await page.reload();
    await expect(page.getByTestId('avatar-photo-fallback')).toBeVisible();
    await expect(page.getByTestId('upload-photo-btn')).toBeVisible();
  });
});
