-- Add SERVICE client type to support OAuth 2.0 Client Credentials flow
-- WEB_APPLICATION: For BFF pattern (Authorization Code + PKCE)
-- SERVICE: For service-to-service authentication (Client Credentials)

-- Update client_type to support both types (WEB_APPLICATION or SERVICE)
ALTER TABLE T_oauth_clients 
  ALTER COLUMN client_type VARCHAR(20) NOT NULL;

-- Update allowed_scopes column to be used for SERVICE clients
-- Space-separated list of scopes allowed for SERVICE clients
ALTER TABLE T_oauth_clients 
  ALTER COLUMN allowed_scopes VARCHAR(500);
