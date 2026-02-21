export type Environment = {
  production: boolean;
  apiUrl: string;
  wsUrl: string;
  shutdownUrl?: string;
};