import { Environment } from "./environment.model";

export const environment: Environment = {
  production: true,
  apiUrl: '',
  wsUrl: '',
  shutdownUrl: 'http://127.0.0.1:8081/actuator/shutdown'
};
