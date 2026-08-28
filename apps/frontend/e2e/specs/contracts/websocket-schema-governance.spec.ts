import { expect, test } from '@playwright/test';
import { GAME_MESSAGE_TYPES } from '../../../src/app/domain/game/messages/default.messages';
import {
  assertBundledSchemasMatchBackendSource,
  assertKnownBackendSchemasAreLoadable,
  knownBackendSchemaTypes,
  loadBackendSchemaForType,
  loadPublishedFrameRegistry,
} from '../../utils/backend-schema-governance';

type JsonSchemaNode = {
  const?: string;
  type?: string | string[];
  required?: string[];
  additionalProperties?: boolean;
  properties?: Record<string, JsonSchemaNode>;
  items?: JsonSchemaNode;
  oneOf?: JsonSchemaNode[];
  anyOf?: JsonSchemaNode[];
  allOf?: JsonSchemaNode[];
};

function declaredConstType(schema: JsonSchemaNode): string | undefined {
  const direct = schema.properties?.['type']?.const;
  if (direct) {
    return direct;
  }

  const nested = [...(schema.oneOf ?? []), ...(schema.anyOf ?? []), ...(schema.allOf ?? [])]
    .map(declaredConstType)
    .filter((value): value is string => Boolean(value));

  const unique = [...new Set(nested)];
  return unique.length === 1 ? unique[0] : undefined;
}

test.describe('Schema governance', () => {
  test('published registry types have one-to-one schema files', async () => {
    assertKnownBackendSchemasAreLoadable();
  });

  test('frontend runtime type registry matches the published backend frame registry', async () => {
    expect([...GAME_MESSAGE_TYPES].sort()).toEqual(
      loadPublishedFrameRegistry().publishedFrameTypes,
    );
  });

  test('schema type discriminator', async () => {
    for (const type of knownBackendSchemaTypes()) {
      const schema = loadBackendSchemaForType(type);
      expect(
        declaredConstType(schema),
        `schema for ${type} should declare type.const=${type}`,
      ).toBe(type);
    }
  });

  test('Stage 1 schemas keep exact sub-state and selected-category contracts', async () => {
    const welcome = loadBackendSchemaForType('welcome');
    const albumsStage = welcome.oneOf?.find(
      (candidate) => candidate.properties?.['stage']?.const === 'albums',
    );
    expect(albumsStage).toBeTruthy();
    expect(albumsStage?.oneOf).toHaveLength(3);

    const albumItem = albumsStage?.properties?.['albums']?.items;
    expect(albumItem?.additionalProperties).toBe(false);
    expect(albumItem?.properties?.['ordinalNumber']?.type).toEqual(['integer', 'null']);

    const selectedShape = albumsStage?.oneOf?.find((candidate) =>
      candidate.required?.includes('selected'),
    );
    expect(selectedShape).toBeTruthy();
    expect(albumsStage?.properties?.['selected']?.properties?.['ordinalNumber']?.type).toBe(
      'integer',
    );

    const albumPicked = loadBackendSchemaForType('album_picked');
    expect(albumPicked.additionalProperties).toBe(false);
    expect(albumPicked.properties?.['selected']?.properties?.['ordinalNumber']?.type).toBe(
      'integer',
    );
  });

  test('bundled schemas match the backend source of truth', async () => {
    assertBundledSchemasMatchBackendSource();
  });
});
