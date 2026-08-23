import { expect, test } from '@playwright/test';
import {
  assertBundledSchemasMatchBackendWorkspace,
  assertKnownBackendSchemasAreLoadable,
  knownBackendSchemaTypes,
  loadBackendSchemaForType,
  loadPublishedFrameRegistry,
} from '../../utils/backend-schema-governance';

type JsonSchemaNode = {
  properties?: {
    type?: {
      const?: string;
    };
  };
  oneOf?: JsonSchemaNode[];
  anyOf?: JsonSchemaNode[];
  allOf?: JsonSchemaNode[];
};

function declaredConstType(schema: JsonSchemaNode): string | undefined {
  const direct = schema.properties?.type?.const;
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
  test('registry schema files', async () => {
    assertKnownBackendSchemasAreLoadable();

    const registryTypes = loadPublishedFrameRegistry().publishedFrameTypes;
    expect(registryTypes).toEqual(knownBackendSchemaTypes());
  });

  test('bundled schemas match backend source', async () => {
    assertBundledSchemasMatchBackendWorkspace();
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
});
