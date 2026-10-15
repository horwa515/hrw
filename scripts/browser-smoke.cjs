// Optional browser integration test. Requires Playwright + a Chromium executable.
// Start the app first, then: CHROMIUM_PATH=/usr/bin/chromium node scripts/browser-smoke.cjs
const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
(async () => {
  const root = path.resolve(__dirname, '..');
  const office = process.env.OFFICE_ENCRYPTED_TEST === '1';
  const output = path.join(root, process.env.BROWSER_OUTPUT || 'build/browser-test');
  await fs.mkdir(output, {recursive:true});
  const browser = await chromium.launch({headless:true, executablePath:process.env.CHROMIUM_PATH || undefined, args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1280,height:980}, acceptDownloads:true});
    const errors=[]; page.on('pageerror', e=>errors.push(e.message));
    await page.goto(process.env.APP_URL || 'http://127.0.0.1:18123/');
    const fixtures=office?['sample-company-3609.pptx','invalid.pptx','sample-company-1.4444.pptx']:['sample-4x3.pptx','invalid.pptx','sample-16x9.pptx'];
    await page.locator('#file-input').setInputFiles(fixtures.map(f=>path.join(root,'examples',f)));
    assert.equal(await page.locator('.file-card').count(),3);
    await page.getByRole('button',{name:'开始转换',exact:true}).click();
    await page.locator('#summary').waitFor({state:'visible',timeout:30000});
    assert.match(await page.locator('#summary').innerText(),/2 个成功，1 个失败/);
    assert.equal(await page.locator('.download-one').count(),2);
    assert.match(await page.locator('.file-card').first().innerText(),office?/27\.52 × 19\.05 cm → 33\.87 × 19\.05 cm/:/25\.40 × 19\.05 cm → 33\.87 × 19\.05 cm/);
    if(office)assert.match(await page.locator('.file-card').first().innerText(),/PPT 打开密码：3609/);
    await page.locator('.file-card').first().locator('summary').click();
    await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
    let pending=page.waitForEvent('download');await page.locator('.download-one').first().click();let d=await pending;
    assert.equal(d.suggestedFilename(),fixtures[0].replace('.pptx','_converted.pptx'));await d.saveAs(path.join(output,d.suggestedFilename()));
    pending=page.waitForEvent('download');await page.locator('#download-all').click();d=await pending;await d.saveAs(path.join(output,'converted-pptx.zip'));
    pending=page.waitForEvent('download');await page.locator('#download-log').click();d=await pending;await d.saveAs(path.join(output,'conversion-log.txt'));
    assert.match(await fs.readFile(path.join(output,'conversion-log.txt'),'utf8'),/转换失败/);
    page.once('dialog',dialog=>dialog.accept());await page.locator('#reset').click();await page.locator('#empty').waitFor({state:'visible'});
    assert.equal(await page.locator('.file-card').count(),0);
    await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
    assert(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth),'Mobile horizontal overflow');
    assert.deepEqual(errors,[],'Browser JavaScript errors');
    console.log('PASS browser: upload, 2 successes / 1 failure, per-file download, ZIP, log, reset, mobile layout');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
