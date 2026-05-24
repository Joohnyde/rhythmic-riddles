import { Stage0Message } from './stage0.messages';
import { Stage1Message } from './stage1.messages';
import { Stage2Message } from './stage2.messages';
import { Stage3Message } from './stage3.messages';

export type GameServerMessage = Stage0Message | Stage1Message | Stage2Message | Stage3Message;
