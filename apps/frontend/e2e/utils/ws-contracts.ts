import { expect } from '@playwright/test';
import Ajv2020 from 'ajv/dist/2020';
import { CapturedWsFrame, backendApplicationFrames } from './ws-capture';

const uuidPattern =
  '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

const ajv = new Ajv2020({ allErrors: true, strict: false });

const teamSchema = {
  type: 'object',
  required: ['id', 'name'],
  properties: {
    id: { type: 'string', pattern: uuidPattern },
    name: { type: 'string' },
    image: {},
  },
  additionalProperties: true,
};

const schemas: Record<string, object> = {
  welcome: {
    type: 'object',
    required: ['type', 'stage'],
    properties: {
      type: { const: 'welcome' },
      stage: { type: ['string', 'number'] },
    },
    additionalProperties: true,
  },
  new_team: {
    type: 'object',
    required: ['type', 'team'],
    properties: { type: { const: 'new_team' }, team: teamSchema },
    additionalProperties: false,
  },
  kick_team: {
    type: 'object',
    required: ['type', 'uuid'],
    properties: { type: { const: 'kick_team' }, uuid: { type: 'string', pattern: uuidPattern } },
    additionalProperties: false,
  },
  album_picked: {
    type: 'object',
    required: ['type', 'selected'],
    properties: { type: { const: 'album_picked' }, selected: { type: 'object' } },
    additionalProperties: false,
  },
  pause: {
    type: 'object',
    required: ['type', 'answeringTeamId', 'interruptId'],
    properties: {
      type: { const: 'pause' },
      answeringTeamId: {
        anyOf: [{ type: 'string', pattern: uuidPattern }, { const: 'null' }, { type: 'null' }],
      },
      interruptId: { type: 'string', pattern: uuidPattern },
    },
    additionalProperties: false,
  },
  answer: {
    type: 'object',
    required: ['type', 'teamId', 'scheduleId', 'correct'],
    properties: {
      type: { const: 'answer' },
      teamId: { type: 'string', pattern: uuidPattern },
      scheduleId: { type: 'string', pattern: uuidPattern },
      correct: { type: 'boolean' },
    },
    additionalProperties: false,
  },
  error_solved: {
    type: 'object',
    required: ['type', 'previousScenario'],
    properties: { type: { const: 'error_solved' }, previousScenario: { type: 'number' } },
    additionalProperties: false,
  },
  song_repeat: {
    type: 'object',
    required: ['type', 'remaining'],
    properties: { type: { const: 'song_repeat' }, remaining: { type: 'number' } },
    additionalProperties: false,
  },
  song_reveal: {
    type: 'object',
    required: ['type'],
    properties: { type: { const: 'song_reveal' } },
    additionalProperties: false,
  },
  song_next: {
    type: 'object',
    required: ['type', 'songId', 'question', 'answer', 'scheduleId', 'answerDuration', 'remaining'],
    properties: {
      type: { const: 'song_next' },
      songId: { type: 'string', pattern: uuidPattern },
      question: { type: 'string' },
      answer: { type: 'string' },
      scheduleId: { type: 'string', pattern: uuidPattern },
      answerDuration: { type: 'number' },
      remaining: { type: 'number' },
    },
    additionalProperties: false,
  },
};

const validators = new Map<string, ReturnType<typeof ajv.compile>>();

export function knownFrontendWsTypes(): string[] {
  return Object.keys(schemas).sort();
}

export function assertFrontendWsContract(frame: Record<string, unknown>): void {
  const type = frame.type;
  expect(typeof type, 'websocket frame must have string type').toBe('string');

  const schema = schemas[type as string];
  expect(schema, `no frontend websocket schema registered for ${String(type)}`).toBeTruthy();

  let validate = validators.get(type as string);
  if (!validate) {
    validate = ajv.compile(schema);
    validators.set(type as string, validate);
  }

  const valid = validate(frame);
  expect(valid, `invalid websocket frame for ${String(type)}: ${ajv.errorsText(validate.errors)}`).toBeTruthy();
}

export function assertAllBackendFramesHaveFrontendContract(frames: CapturedWsFrame[]): void {
  for (const frame of backendApplicationFrames(frames)) {
    assertFrontendWsContract(frame.json!);
  }
}
