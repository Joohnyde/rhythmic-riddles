export interface DefaultMessage {
  type: string;
}

export interface WelcomeMessage extends DefaultMessage {
  stage: string;
}
