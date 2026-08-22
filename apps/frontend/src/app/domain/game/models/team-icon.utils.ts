const TEAM_ICON_COLOR = /_([0-9a-f]{6})\.(?:png|jpg)$/i;

export function teamIconColor(image: string): string | undefined {
  const match = TEAM_ICON_COLOR.exec(image);
  return match ? `#${match[1].toUpperCase()}` : undefined;
}
