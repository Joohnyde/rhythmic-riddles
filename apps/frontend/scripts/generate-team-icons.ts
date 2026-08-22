import { readdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const iconsDirectory = resolve('public/team-icons');
const outputFile = resolve('src/app/domain/game/generated/team-icons.generated.ts');
const imageExtension = /\.(?:png|jpg)$/i;
const iconFileName = /_([0-9a-f]{6})\.(?:png|jpg)$/i;

const imageFiles = readdirSync(iconsDirectory)
  .filter((file) => imageExtension.test(file))
  .sort((left, right) => left.localeCompare(right));

const invalidFileNames = imageFiles.filter((file) => !iconFileName.test(file));
if (invalidFileNames.length > 0) {
  throw new Error(
    `Team icon filenames must end in _HEXCODE.png/.jpg: ${invalidFileNames.join(', ')}`,
  );
}

const icons = imageFiles.map((file) => `/team-icons/${file}`);
const contents =
  `// AUTO-GENERATED FILE. DO NOT EDIT.\n` +
  `export const TEAM_ICONS: readonly string[] = ${JSON.stringify(icons, null, 2)};\n`;

writeFileSync(outputFile, contents);
