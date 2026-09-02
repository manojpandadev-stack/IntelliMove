import { test, expect, Page, Locator } from '@playwright/test';

/**
 * Regression: invisible input text in OS/browser Dark Mode.
 *
 * Root cause: `:root { color-scheme: light dark }` let Chromium auto-style
 * form controls with its dark-mode defaults which can render invisible on
 * the app surface.
 *
 * This test proves the fix for BOTH light and dark color schemes:
 * the design system pins an explicit readable style on every form control.
 * Under the Dark Rose theme the pinned pair is light text (#FFF1F5) on the
 * deep rose-black input field (#1A0D14), a visible caret, and a readable
 * placeholder (#B98A9C ≈6.3:1). Typed values must be present and masked
 * for password fields.
 */

const INPUT_TEXT = 'rgb(255, 241, 245)'; // #FFF1F5 primary text
const INPUT_BG = 'rgb(26, 13, 20)';      // #1A0D14 input background

async function fillAndVerify(page: Page, selector: string, value: string) {
  const input = page.locator(selector);
  await input.fill(value);
  await expect(input).toHaveValue(value);
  return input;
}

function contrastOf(rgb: string, rgbBg: string): number {
  const parse = (c: string) => c.match(/\d+/g)?.slice(0, 3).map(Number) ?? [0, 0, 0];
  const [r1, g1, b1] = parse(rgb);
  const [r2, g2, b2] = parse(rgbBg);
  const lum = (r: number, g: number, b: number) => {
    const f = (v: number) => (v / 255 <= 0.03928 ? v / 255 / 12.92 : Math.pow((v / 255 + 0.055) / 1.055, 2.4));
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };
  const l1 = lum(r1, g1, b1);
  const l2 = lum(r2, g2, b2);
  const [hi, lo] = l1 > l2 ? [l1, l2] : [l2, l1];
  return (hi + 0.05) / (lo + 0.05);
}

async function verifyReadableStyles(page: Page, input: Locator, label: string) {
  const style = await input.evaluate((el: HTMLInputElement) => {
    const cs = getComputedStyle(el);
    const ph = getComputedStyle(el, '::placeholder');
    return {
      color: cs.color,
      backgroundColor: cs.backgroundColor,
      caretColor: cs.caretColor,
      colorScheme: cs.colorScheme,
      placeholderColor: ph.color,
    };
  });

  expect(style.color, `${label}: text color is light`).toBe(INPUT_TEXT);
  expect(style.backgroundColor, `${label}: dark input background`).toBe(INPUT_BG);
  expect(style.caretColor, `${label}: caret is visible (light)`).toBe(INPUT_TEXT);
  // text must NOT be unreadable against the input surface; require strong contrast
  const ratio = contrastOf(style.color, style.backgroundColor);
  expect(ratio, `${label}: contrast ratio`).toBeGreaterThanOrEqual(7);
  // placeholder must be a readable grey (never white / currentColor-on-white)
  const phRatio = contrastOf(style.placeholderColor, style.backgroundColor);
  expect(phRatio, `${label}: placeholder contrast`).toBeGreaterThanOrEqual(4);
  expect(style.placeholderColor, `${label}: placeholder not white`).not.toBe('rgb(255, 255, 255)');
  return style;
}

for (const scheme of ['light', 'dark'] as const) {
  test.describe(`Input visibility — ${scheme} mode`, () => {
    test.use({ colorScheme: scheme });

    test('Register: typed values present + readable computed styles', async ({ page }) => {
      await page.goto('/register');
      const fields = [
        { sel: 'form input >>nth=0', label: 'First Name', value: 'Jane' },
        { sel: 'form input >>nth=1', label: 'Last Name', value: 'Rider' },
        { sel: 'form input >>nth=2', label: 'Email', value: 'jane@example.com' },
        { sel: 'form input >>nth=3', label: 'Password', value: 'StrongPass9!' },
      ];
      for (const f of fields) {
        const input = await fillAndVerify(page, f.sel, f.value);
        const s = await verifyReadableStyles(page, input, `Register:${f.label}`);
      }

      // Password must remain masked
      const pwd = page.locator('form input[type="password"]');
      await expect(pwd).toHaveAttribute('type', 'password');
      await expect(pwd).toHaveValue('StrongPass9!');
      const pwdType = await pwd.evaluate((el: HTMLInputElement) => el.type);
      expect(pwdType, 'password input keeps type=password (masked)').toBe('password');

      // Placeholder text visible BEFORE typing on the password field (type into it last, check sibling)
      expect(page.locator('form input >>nth=0')).toHaveAttribute('placeholder', 'First name');
      expect(page.locator('form input[type="email"]')).toHaveAttribute('placeholder', 'Email');
      expect(pwd).toHaveAttribute('placeholder', 'Password (min 8 characters)');
    });

    test('Login: email + password values visible with readable styles', async ({ page }) => {
      await page.goto('/login');
      const email = await fillAndVerify(page, 'input[type="email"]', 'login@example.com');
      const pwd = await fillAndVerify(page, 'input[type="password"]', 'LoginPass1!');
      await verifyReadableStyles(page, email, 'Login: Email');
      await verifyReadableStyles(page, pwd, 'Login: Password');
      // placeholder visibility before typing
      expect(await page.locator('input[type="email"]')).toHaveAttribute('placeholder', 'Email');
      expect(await page.locator('input[type="password"]')).toHaveAttribute('placeholder', 'Password');
    });
  });
}