import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { chromium } from '@playwright/test';
import { GIFEncoder, quantize, applyPalette } from 'gifenc';

const dist = resolve('dist/cestereg/browser');
const output = resolve('../backend/src/main/resources/splash');
const viewport = { width: 900, height: 600 };
const frameDelay = 50;
const origin = 'http://splash.test';

async function generate(): Promise<void> {
  if (!existsSync(resolve(dist, 'index.html'))) {
    throw new Error(`Angular build missing at ${dist}. Run npm run build first.`);
  }

  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({
      viewport,
      deviceScaleFactor: 1,
      reducedMotion: 'no-preference',
    });
    // Fulfill the navigation and Angular assets directly from disk; no listening socket.
    await page.route('**/*', async (route) => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) return route.abort();
      const pathname = url.pathname === '/startup-splash' ? '/index.html' : url.pathname;
      const path = resolve(dist, `.${decodeURIComponent(pathname)}`);
      if (!path.startsWith(dist + sep) || !existsSync(path)) {
        return route.fulfill({ status: 404 });
      }
      return route.fulfill({ path });
    });
    page.on('pageerror', (error) => console.error(error));
    page.on('response', (response) => {
      if (!response.ok()) console.error(`${response.status()} ${response.url()}`);
    });
    const response = await page.goto(`${origin}/startup-splash`, { waitUntil: 'domcontentloaded' });
    if (!response?.ok()) throw new Error('Could not load the startup splash page');
    const animation = page.getByTestId('splash-animation');
    await animation.waitFor();
    await page.waitForLoadState('networkidle');
    await page.evaluate(() => document.fonts.ready.then(() => undefined));

    const box = await animation.boundingBox();
    if (!box) throw new Error('Missing splash animation bounds');
    const x = Math.floor(box.x);
    const y = Math.floor(box.y);
    const width = Math.ceil(box.x + box.width) - x;
    const height = Math.ceil(box.y + box.height) - y;
    if (x < 0 || y < 0 || x + width > viewport.width || y + height > viewport.height) {
      throw new Error('Splash animation must fit inside the viewport');
    }

    const duration = await animation.evaluate((element) => {
      const loops = element.getAnimations({ subtree: true });
      for (const effect of document.getAnimations()) {
        if (loops.includes(effect)) {
          effect.pause();
          effect.currentTime = 0;
        } else if (effect.effect?.getTiming().iterations !== Infinity) {
          effect.finish();
        }
      }
      return Number(loops[0]?.effect?.getTiming().duration);
    });
    if (!Number.isFinite(duration) || duration <= 0) throw new Error('Missing splash animation');

    const background = await page.screenshot();
    const gif = GIFEncoder();
    const frameCount = Math.max(1, Math.round(duration / frameDelay));
    for (let i = 0; i < frameCount; i++) {
      await animation.evaluate(
        (element, time) => {
          for (const effect of element.getAnimations({ subtree: true })) effect.currentTime = time;
        },
        (i * duration) / frameCount,
      );
      const png = (await page.screenshot({ clip: { x, y, width, height } })).toString('base64');
      const rgba = Uint8Array.from(
        await page.evaluate(
          async ({ png, width, height }) => {
            const bitmap = await createImageBitmap(
              await (await fetch(`data:image/png;base64,${png}`)).blob(),
            );
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const context = canvas.getContext('2d')!;
            context.drawImage(bitmap, 0, 0);
            bitmap.close();
            return Array.from(context.getImageData(0, 0, width, height).data);
          },
          { png, width, height },
        ),
      );
      // Only the captured animation rectangle is opaque in the full-size GIF overlay.
      const palette = quantize(rgba, 255);
      const pixels = applyPalette(rgba, palette);
      const frame = new Uint8Array(viewport.width * viewport.height);
      for (let row = 0; row < height; row++) {
        for (let col = 0; col < width; col++) {
          frame[(y + row) * viewport.width + x + col] = pixels[row * width + col] + 1;
        }
      }
      gif.writeFrame(frame, viewport.width, viewport.height, {
        palette: [[0, 0, 0], ...palette],
        transparent: true,
        transparentIndex: 0,
        dispose: 1,
        delay: frameDelay,
        repeat: 0,
      });
    }
    gif.finish();

    mkdirSync(output, { recursive: true });
    writeFileSync(resolve(output, 'starting.png'), background);
    writeFileSync(resolve(output, 'animation.gif'), gif.bytes());
  } finally {
    await browser.close();
  }
}

generate().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
