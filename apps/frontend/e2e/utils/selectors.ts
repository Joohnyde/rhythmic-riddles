import { Locator, Page, expect } from '@playwright/test';
import { Role } from './env';

function rolePrefixedOrLegacy(page: Page, role: Role, roleId: string, legacyId: string): Locator {
  return page.getByTestId(`${role}-${roleId}`).or(page.getByTestId(legacyId));
}

export const selectors = {
  loginForm: (page: Page, role: Role) =>
    rolePrefixedOrLegacy(page, role, 'login-form', 'login-form'),
  roomCodeInput: (page: Page, role: Role) =>
    rolePrefixedOrLegacy(page, role, 'room-code-input', 'login-room-code-input').or(
      page.getByPlaceholder(/room code/i),
    ),
  loginButton: (page: Page, role: Role) =>
    rolePrefixedOrLegacy(page, role, 'login-button', 'login-submit-button').or(
      page.getByRole('button', { name: /log in/i }),
    ),
  loginError: (page: Page, role: Role) =>
    rolePrefixedOrLegacy(page, role, 'login-error', 'login-error'),

  // Page roots. Keep them tolerant so selector contract can survive small naming differences.
  adminLobbyPage: (page: Page) =>
    page.getByTestId('admin-lobby-page').or(page.getByText(/lobby/i)).first(),
  tvLobbyPage: (page: Page) =>
    page.getByTestId('tv-lobby-page').or(page.getByText(/lobby/i)).first(),
  adminAlbumsPage: (page: Page) =>
    page.getByTestId('admin-albums-page').or(page.getByText(/album/i)).first(),
  tvAlbumsPage: (page: Page) =>
    page.getByTestId('tv-albums-page').or(page.getByText(/album/i)).first(),
  adminSongsPage: (page: Page) =>
    page
      .getByTestId('admin-song-round-page')
      .or(page.getByText(/stage 2/i))
      .first(),
  tvSongsPage: (page: Page) =>
    page
      .getByTestId('tv-song-round-page')
      .or(page.getByText(/stage 2/i))
      .first(),
  adminWinnerPage: (page: Page) =>
    page
      .getByTestId('admin-winner-page')
      .or(page.getByText(/winner|score/i))
      .first(),
  tvWinnerPage: (page: Page) =>
    page
      .getByTestId('tv-winner-page')
      .or(page.getByText(/winner|score/i))
      .first(),

  adminRepeatSongButton: (page: Page) =>
    page.getByTestId('admin-repeat-song-button').or(page.getByRole('button', { name: /repeat/i })),
  adminRevealAnswerButton: (page: Page) =>
    page
      .getByTestId('admin-reveal-answer-button')
      .or(page.getByRole('button', { name: /reveal/i })),
  adminAnswerCorrectButton: (page: Page) =>
    page
      .getByTestId('admin-answer-correct-button')
      .or(page.getByRole('button', { name: /correct/i })),
  adminAnswerWrongButton: (page: Page) =>
    page
      .getByTestId('admin-answer-wrong-button')
      .or(page.getByRole('button', { name: /wrong|incorrect/i })),
  adminResolveErrorButton: (page: Page) =>
    page
      .getByTestId('admin-resolve-error-button')
      .or(page.getByRole('button', { name: /resolve|solved/i })),
  adminNextSongButton: (page: Page) =>
    page
      .getByTestId('admin-next-song-after-reveal-button')
      .or(page.getByRole('button', { name: /next/i })),
};

export async function expectVisible(locator: Locator, message: string): Promise<void> {
  await expect(locator, message).toBeVisible();
}
