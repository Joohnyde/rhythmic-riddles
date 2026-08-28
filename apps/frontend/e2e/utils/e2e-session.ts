import { Browser, BrowserContextOptions, Page } from '@playwright/test';
import { LoginPage } from '../pages/login-page';
import { Role } from './env';
import {
  CapturedWsFrame,
  captureReceivedWebSocketFrames,
  expectBackendWsFrameType,
} from './ws-capture';

export type ConnectedClient = { page: Page; frames: CapturedWsFrame[]; close: () => Promise<void> };

export async function connectRole(
  browser: Browser,
  role: Role,
  roomCode: string,
  contextOptions: BrowserContextOptions = {},
): Promise<ConnectedClient> {
  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();
  const frames = captureReceivedWebSocketFrames(page);
  const login = new LoginPage(page);
  await login.open(role);
  await login.login(role, roomCode);
  await login.expectConnected(role);
  await expectBackendWsFrameType(frames, 'welcome');
  return { page, frames, close: () => context.close() };
}

export async function connectAdminAndTv(
  browser: Browser,
  roomCode: string,
  contextOptions: BrowserContextOptions = {},
): Promise<{ admin: ConnectedClient; tv: ConnectedClient; close: () => Promise<void> }> {
  // Parallel connection keeps time-sensitive song-state fixtures inside the legal 9.6s buzz window.
  const [admin, tv] = await Promise.all([
    connectRole(browser, 'admin', roomCode, contextOptions),
    connectRole(browser, 'tv', roomCode, contextOptions),
  ]);
  return {
    admin,
    tv,
    close: async () => {
      await admin.close().catch(() => undefined);
      await tv.close().catch(() => undefined);
    },
  };
}
export async function attemptRoleConnection(
  browser: Browser,
  role: Role,
  roomCode: string,
): Promise<ConnectedClient> {
  const context = await browser.newContext();
  const page = await context.newPage();
  const frames = captureReceivedWebSocketFrames(page);
  const login = new LoginPage(page);

  await login.open(role);
  await login.login(role, roomCode);

  // Do not require connected route here. This helper is intentionally used for
  // duplicate same-role socket attempts, which should often remain on login.
  await new Promise((resolve) => setTimeout(resolve, 750));

  return { page, frames, close: () => context.close() };
}
