/** Retry only initial navigation or a documented static resource exhaustion, before business actions.
 * Edge can reject a cold Vite module with net::ERR_INSUFFICIENT_RESOURCES on this host.
 * API errors, script errors and business assertions never trigger this retry.
 */
export async function openPreview(page, path, readyLocator) {
  let exhaustedStaticResource = false;
  let reloadDocument = false;
  const failed = request => {
    if (['script', 'stylesheet'].includes(request.resourceType())
      && request.failure()?.errorText === 'net::ERR_INSUFFICIENT_RESOURCES') exhaustedStaticResource = true;
  };
  page.on('requestfailed', failed);
  try {
    for (let attempt = 0; attempt < 2; attempt++) {
      let phase = 'navigation';
      try {
        if (reloadDocument) await page.reload({ waitUntil: 'domcontentloaded', timeout: 15000 });
        else await page.goto(path, { waitUntil: 'domcontentloaded', timeout: 15000 });
        phase = 'mount';
        if (readyLocator) await readyLocator.waitFor({ state: 'visible', timeout: 15000 });
        return;
      } catch (error) {
        if (attempt || error.name !== 'TimeoutError' || (phase === 'mount' && !exhaustedStaticResource)) throw error;
        console.warn(`[preview] Initial ${phase === 'mount' ? 'static module exhausted browser resources' : 'navigation stalled'}; retrying once before fixture actions.`);
        // A repeated goto to an H5 hash URL can be a same-document navigation.
        reloadDocument = phase === 'mount';
        exhaustedStaticResource = false;
      }
    }
  } finally {
    page.off('requestfailed', failed);
  }
}
