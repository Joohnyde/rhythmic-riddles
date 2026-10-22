/** Retry a room mutation only when its fail-fast NOWAIT room lock returned 423/E010.
 * Other statuses are returned unchanged so tests still detect real state failures.
 * Use only with operations whose 423 contract is exclusively RoomBusyException.
 */
export async function retryRoomBusy(operation: () => Promise<number>): Promise<number> {
  const maxAttempts = 5;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const status = await operation();
    if (status !== 423 || attempt === maxAttempts) return status;
    await new Promise<void>((resolve) => setTimeout(resolve, attempt * 100));
  }
  throw new Error('Unreachable room retry state');
}
