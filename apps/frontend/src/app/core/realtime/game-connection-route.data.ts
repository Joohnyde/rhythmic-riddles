/**
 * Route-data key used to mark pages that belong to an active game connection.
 *
 * GameRealtimeService reads this marker after successful Angular navigations.
 * Moving between routes with the same surface keeps the WebSocket alive; leaving
 * the marked route tree closes it normally and clears the in-memory game session.
 */
export const GAME_CONNECTION_SURFACE_ROUTE_DATA = 'gameConnectionSurface';
