import { expect, test } from '@playwright/test';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import {
  addTeamThroughUi,
  buzz,
  createProductSession,
  expectScore,
  next,
  pickAndStartAlbum,
  ProductSession,
  ProductTeam,
  startGame,
} from '../../utils/product-game';

test.describe.serial('Full product journeys', () => {
  test('golden game reaches real results with a meaningful winner', async ({
    browser,
    request,
  }) => {
    const session = await createProductSession(browser, request, 1, 1);
    try {
      const [teamA, teamB] = await createTeams(session, request);
      await startGame(session.admin);
      await expect(session.admin.getByText('Admin', { exact: true })).toBeVisible();
      await pickAndStartAlbum(session.admin, session.tv.page, session.catalog[0]);

      await buzz(request, teamA, session.admin, session.tv.page);
      await session.admin.getByTestId('admin-answer-correct-button').click();
      await expectScore(session.admin, teamA, 30);
      await expect(session.tv.page.getByTestId('song-bravo-team')).toContainText('Team Aurora: 30');
      await next(session.admin);

      await expectWinner(session, teamA, 30, teamB, 0);
    } finally {
      await session.close();
    }
  });

  test('wrong answer allows another team to buzz and win', async ({ browser, request }) => {
    const session = await createProductSession(browser, request, 1, 1);
    try {
      const [teamA, teamB] = await createTeams(session, request);
      await startGame(session.admin);
      await pickAndStartAlbum(session.admin, session.tv.page, session.catalog[0]);

      await buzz(request, teamA, session.admin, session.tv.page);
      await session.admin.getByTestId('admin-answer-wrong-button').click();
      await expectScore(session.admin, teamA, -10);
      await expect(session.admin.getByTestId('admin-answer-correct-button')).toBeHidden();

      await buzz(request, teamB, session.admin, session.tv.page);
      await session.admin.getByTestId('admin-answer-correct-button').click();
      await expectScore(session.admin, teamB, 30);
      await expect(session.tv.page.getByTestId('song-bravo-team')).toContainText(
        'Team Borealis: 30',
      );
      await next(session.admin);

      await expectWinner(session, teamB, 30, teamA, -10);
    } finally {
      await session.close();
    }
  });

  test('multi-song and multi-album lifecycle preserves scores and rotates to Admin picker', async ({
    browser,
    request,
  }) => {
    test.slow();
    const session = await createProductSession(browser, request, 2, 3);
    try {
      const [teamA, teamB] = await createTeams(session, request);
      await startGame(session.admin);
      const firstPicker = await pickerText(session.admin);
      await pickAndStartAlbum(session.admin, session.tv.page, session.catalog[0]);

      await answerCorrectAndNext(request, session, teamA, 30, true);
      await expect(session.admin.getByTestId('admin-song-round-page')).toBeVisible();
      await expectScore(session.admin, teamA, 30);
      await answerCorrectAndNext(request, session, teamA, 60, false);
      await expect(session.admin.getByTestId('admin-albums-page')).toBeVisible();
      await expect(
        session.admin.getByTestId(`admin-album-card-${session.catalog[0].id}`).getByRole('button'),
      ).toBeDisabled();

      const secondPicker = await pickerText(session.admin);
      expect(secondPicker).not.toBe(firstPicker);
      await pickAndStartAlbum(session.admin, session.tv.page, session.catalog[1]);
      await expectScore(session.admin, teamA, 60);
      await answerCorrectAndNext(request, session, teamB, 30, true);
      await answerCorrectAndNext(request, session, teamB, 60, false);

      await expect(session.admin.getByTestId('admin-albums-page')).toBeVisible();
      await expect(session.admin.getByText('Admin', { exact: true })).toBeVisible();
      await pickAndStartAlbum(session.admin, session.tv.page, session.catalog[2]);
      await expectScore(session.admin, teamA, 60);
      await expectScore(session.admin, teamB, 60);
      await expectScore(session.tv.page, teamA, 60);
    } finally {
      await session.close();
    }
  });

  test('TV reconnects while a team answers and scoring remains exactly once', async ({
    browser,
    request,
  }) => {
    const session = await createProductSession(browser, request, 1, 1);
    let recoveredTv: Awaited<ReturnType<typeof connectRole>> | undefined;
    try {
      const [teamA] = await createTeams(session, request);
      await startGame(session.admin);
      await pickAndStartAlbum(session.admin, session.tv.page, session.catalog[0]);
      await buzz(request, teamA, session.admin, session.tv.page);

      await session.tv.close();
      const failedAnswer = session.admin.waitForResponse(
        (response) => response.url().includes('/answer') && response.request().method() === 'POST',
      );
      await session.admin.getByTestId('admin-answer-correct-button').click();
      expect((await failedAnswer).status()).toBe(503);
      await expectScore(session.admin, teamA, 0);

      recoveredTv = await connectRole(browser, 'tv', session.roomCode, {
        viewport: { width: 1440, height: 900 },
      });
      await expect(recoveredTv.page.getByTestId('answering-team-name')).toHaveText(teamA.name);
      await expect(session.admin.getByTestId('answering-team-name')).toHaveText(teamA.name);
      await session.admin.getByTestId('admin-answer-correct-button').click();
      await expectScore(session.admin, teamA, 30);
      await expect(recoveredTv.page.getByTestId('song-bravo-team')).toContainText(
        'Team Aurora: 30',
      );
      await next(session.admin);
      await expect(session.admin.getByTestId(`admin-winner-team-row-${teamA.id}`)).toContainText(
        '30',
      );
    } finally {
      await recoveredTv?.close().catch(() => undefined);
      await session.close();
    }
  });

  test('technical interruption during playback recovers and gameplay resumes', async ({
    browser,
    request,
  }) => {
    const session = await createProductSession(browser, request, 1, 1);
    let recoveredTv: Awaited<ReturnType<typeof connectRole>> | undefined;
    try {
      const [teamA] = await createTeams(session, request);
      await startGame(session.admin);
      await pickAndStartAlbum(session.admin, session.tv.page, session.catalog[0]);

      await session.tv.close();
      await expect(session.admin.getByTestId('system-error-panel')).toBeVisible();
      await expect(session.admin.getByTestId('admin-resolve-error-button')).toBeEnabled();

      recoveredTv = await connectRole(browser, 'tv', session.roomCode, {
        viewport: { width: 1440, height: 900 },
      });
      await expect(recoveredTv.page.getByTestId('system-error-panel')).toBeVisible();

      await session.admin.getByTestId('admin-resolve-error-button').click();
      await expect(session.admin.getByTestId('system-error-panel')).toBeHidden();
      await expect(recoveredTv.page.getByTestId('tv-snippet-audio')).toBeVisible();

      await buzz(request, teamA, session.admin, recoveredTv.page);
      await session.admin.getByTestId('admin-answer-correct-button').click();
      await expectScore(session.admin, teamA, 30);
      await expect(recoveredTv.page.getByTestId('song-bravo-team')).toContainText(
        'Team Aurora: 30',
      );
    } finally {
      await recoveredTv?.close().catch(() => undefined);
      await session.close();
    }
  });

  test('selected album survives fresh Admin and TV recovery before start', async ({
    browser,
    request,
  }) => {
    const session = await createProductSession(browser, request, 1, 2);
    let recovered: Awaited<ReturnType<typeof connectAdminAndTv>> | undefined;
    try {
      await createTeams(session, request);
      await startGame(session.admin);
      const selected = session.catalog[0];
      await session.admin
        .getByTestId(`admin-album-card-${selected.id}`)
        .getByRole('button')
        .click();
      await session.admin
        .getByRole('dialog')
        .filter({ hasText: `Choose ${selected.name}?` })
        .getByRole('button', { name: 'YES' })
        .click();
      await expect(session.admin.getByTestId('admin-album-focus')).toHaveAttribute(
        'data-focus-state',
        'settled',
        { timeout: 10_000 },
      );

      await session.tv.close();
      await session.adminContext.close();
      recovered = await connectAdminAndTv(browser, session.roomCode, {
        viewport: { width: 1440, height: 900 },
      });
      await expect(
        recovered.admin.page.getByTestId(`admin-album-focus-card-${selected.id}`),
      ).toHaveAttribute('data-selected', 'true');
      await expect(
        recovered.tv.page.getByTestId(`tv-album-focus-card-${selected.id}`),
      ).toHaveAttribute('data-selected', 'true');
      await expect(recovered.admin.page.getByTestId('admin-start-songs-button')).toBeEnabled();
      await recovered.admin.page.getByTestId('admin-start-songs-button').click();
      await recovered.admin.page
        .getByRole('dialog')
        .filter({ hasText: 'Start the game?' })
        .getByRole('button', { name: 'YES' })
        .click();
      await expect(recovered.admin.page.getByTestId('admin-song-round-page')).toBeVisible();
      await expect(recovered.tv.page.getByTestId('tv-song-round-page')).toBeVisible();
    } finally {
      await recovered?.close().catch(() => undefined);
      await session.close();
    }
  });
});

async function createTeams(
  session: ProductSession,
  request: Parameters<typeof addTeamThroughUi>[0],
): Promise<[ProductTeam, ProductTeam]> {
  const teamA = await addTeamThroughUi(request, session.admin, 'Team Aurora', '710001');
  const teamB = await addTeamThroughUi(request, session.admin, 'Team Borealis', '710002');
  await expect(session.tv.page.getByText(teamA.name, { exact: true })).toBeVisible();
  await expect(session.tv.page.getByText(teamB.name, { exact: true })).toBeVisible();
  return [teamA, teamB];
}

async function answerCorrectAndNext(
  request: Parameters<typeof buzz>[0],
  session: ProductSession,
  team: ProductTeam,
  expectedScore: number,
  expectsNextSong: boolean,
): Promise<void> {
  await buzz(request, team, session.admin, session.tv.page);
  await session.admin.getByTestId('admin-answer-correct-button').click();
  await expectScore(session.admin, team, expectedScore);
  await next(session.admin);
  if (expectsNextSong) {
    await expect(session.tv.page.getByTestId('tv-snippet-audio')).toBeVisible();
  }
}

async function pickerText(page: ProductSession['admin']): Promise<string> {
  const header = page.locator('rr-stage1-category-header');
  await expect(header).toContainText('Now picking:');
  return (await header.textContent())!.replace(/\s+/g, ' ').trim();
}

async function expectWinner(
  session: ProductSession,
  winner: ProductTeam,
  winnerScore: number,
  runnerUp: ProductTeam,
  runnerUpScore: number,
): Promise<void> {
  for (const page of [session.admin, session.tv.page]) {
    await expect(page.getByText('Winner', { exact: true })).toBeVisible();
    await expect(page.locator(`[data-testid$="winner-team-row-${winner.id}"]`)).toContainText(
      `${winner.name}${winnerScore}`,
    );
    await expect(page.locator(`[data-testid$="winner-team-row-${runnerUp.id}"]`)).toContainText(
      `${runnerUp.name}${runnerUpScore}`,
    );
  }
}
