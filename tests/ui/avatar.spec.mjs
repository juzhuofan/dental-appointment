import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const env = Object.fromEntries(readFileSync(new URL('../../.env.local', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => /^[A-Z_][A-Z0-9_]*=/.test(line))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));

async function expectLoadedImage(image) {
  await expect(image).toBeVisible();
  await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth > 0), { timeout: 20000 }).toBe(true);
}

test('科室、医生和工作人员头像在管理端正常显示', async ({ page }) => {
  test.setTimeout(120000);
  if (process.env.AVATAR_TEST_API_BASE) {
    await page.route('**/api/v1/**', async route => {
      const requested = new URL(route.request().url());
      const target = new URL(requested.pathname + requested.search, process.env.AVATAR_TEST_API_BASE);
      await route.fulfill({ response: await route.fetch({ url: target.toString() }) });
    });
  }
  const password = env.DEMO_ADMIN_PASSWORD;
  if (!password) throw new Error('本地配置缺少管理员演示账号密码');
  await page.goto('/login');
  await page.getByPlaceholder('请输入账号', { exact: true }).fill(env.DEMO_ADMIN_USERNAME || 'admin');
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/dashboard/);
  await expectLoadedImage(page.locator('.profile-button .avatar img'));

  await page.goto('/departments');
  const department = page.locator('.el-table__body tr').filter({ hasText: '牙体牙髓科' });
  await expectLoadedImage(department.locator('.el-avatar img'));
  await department.getByRole('button', { name: '编辑' }).click();
  const dialog = page.getByRole('dialog');
  const preview = dialog.locator('.avatar-editor .el-avatar img');
  await expectLoadedImage(preview);
  await expect(dialog.locator('button.el-button').filter({ hasText: '选择图片上传 OSS' })).toBeVisible();
  const signed = new URL(await preview.getAttribute('src'));
  const stableUrl = signed.origin + signed.pathname;
  let uploadSeen = false;
  await page.route('**/api/v1/files/upload', async route => {
    uploadSeen = true;
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers()['content-type']).toContain('multipart/form-data');
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      code: 'OK', data: { url: stableUrl, objectKey: 'test/avatar.png', originalFilename: 'avatar.png', size: 70, contentType: 'image/png' },
    }) });
  });
  await dialog.locator('input[type="file"]').setInputFiles({ name: 'avatar.png', mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==', 'base64') });
  await expect(preview).toHaveAttribute('src', /^blob:/);
  expect(uploadSeen).toBe(true);
  await dialog.getByRole('button', { name: '取消' }).click();

  await page.goto('/doctors');
  const doctor = page.locator('.el-table__body tr').filter({ hasText: '林知安' });
  await expectLoadedImage(doctor.locator('.el-avatar img'));

  await page.goto('/users');
  await page.getByPlaceholder('搜索账号名称或关键词').fill('admin');
  await page.getByRole('button', { name: '查询', exact: true }).click();
  const admin = page.locator('.el-table__body tr').filter({ hasText: 'admin' });
  await expectLoadedImage(admin.locator('.el-avatar img'));
});
