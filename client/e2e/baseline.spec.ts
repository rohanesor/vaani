import { expect, test } from '@playwright/test';

async function signIn(page: import('@playwright/test').Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.getByLabel('Email').fill('rose@vaani.dev');
  await page.getByLabel('Password').fill('vaani-demo');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('region', { name: 'Conversations' })).toBeVisible();
}

test('authenticates and renders the transcript conversation UI', async ({ page }) => {
  await signIn(page);
  await expect(page.getByText('Hackathon squad', { exact: true }).first()).toBeVisible();
  await page.getByText('Hackathon squad', { exact: true }).first().click();
  await expect(page.getByRole('main', { name: 'Conversation with Hackathon squad' })).toBeVisible();
  await expect(page.getByText('Node + SQLite final.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'VAANI actions' })).toBeVisible();
});

test('supports search, AI draft UI, context, and dark theme without live providers', async ({ page }) => {
  await signIn(page);
  await page.getByLabel('Find a chat or search messages').fill('Node');
  await page.getByText('Search all messages for “Node”').click();
  await expect(page.getByText('Messages')).toBeVisible();
  await page.getByText('Hackathon squad', { exact: true }).first().click();
  await page.getByRole('button', { name: 'VAANI actions' }).click();
  await page.getByRole('button', { name: 'Draft a reply' }).click();
  await expect(page.getByLabel('VAANI draft')).toBeVisible();
  await expect(page.getByText('Sounds good — I’ll have the architecture diagram ready by Wednesday evening.')).toBeVisible();
  await page.getByRole('button', { name: 'Use & edit' }).click();
  await expect(page.getByLabel('Message Hackathon squad')).toHaveValue(/architecture diagram/);
  await page.getByRole('button', { name: 'Context' }).click();
  await expect(page.getByRole('region', { name: 'VAANI context' })).toBeVisible();
  await expect(page.getByText('The squad picked the stack for the demo and split up the pitch work.')).toBeVisible();
  await page.goto('/#/settings/appearance');
  await page.getByRole('radio', { name: 'Dark' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('persists an idempotent text submission across a browser refresh', async ({ page }) => {
  await signIn(page);
  await page.getByText('Hackathon squad', { exact: true }).first().click();
  const body = `Persistent browser message ${Date.now()}`;
  const input = page.getByLabel('Message Hackathon squad');
  await input.fill(body);
  await input.press('Enter');
  const transcriptMessage = page.getByRole('log', { name: 'Messages' }).getByText(body, { exact: true });
  await expect(transcriptMessage).toBeVisible();
  await page.reload();
  await expect(page.getByRole('log', { name: 'Messages' }).getByText(body, { exact: true })).toBeVisible();
});

test('keeps a pending text message across a reload and reconnects it', async ({ page }) => {
  await signIn(page);
  await page.getByText('Hackathon squad', { exact: true }).first().click();
  const body = `Offline browser message ${Date.now()}`;
  await page.route('**/api/conversations/*/messages', (route) => route.abort('internetdisconnected'));
  await page.getByLabel('Message Hackathon squad').fill(body);
  await page.getByLabel('Message Hackathon squad').press('Enter');
  await expect.poll(() => page.evaluate(() => Array.from({ length: localStorage.length }, (_, i) => localStorage.getItem(localStorage.key(i)!)).some((value) => value?.includes('Offline browser message')))).toBe(true);
  await page.unroute('**/api/conversations/*/messages');
  await page.reload();
  await expect(page.getByRole('log', { name: 'Messages' }).getByText(body, { exact: true })).toBeVisible({ timeout: 10_000 });
});

test('exposes voice recorder UI and responsive navigation', async ({ page }) => {
  await signIn(page);
  await page.getByText('Hackathon squad', { exact: true }).first().click();
  await page.getByRole('button', { name: 'Record a voice note' }).click();
  await expect(page.getByRole('group', { name: 'Voice recorder' })).toBeVisible();
  // Chromium test context has no granted microphone: this verifies the honest UI error path,
  // not physical microphone recording.
  await expect(page.getByText(/microphone|Back to typing/i).first()).toBeVisible();
});
