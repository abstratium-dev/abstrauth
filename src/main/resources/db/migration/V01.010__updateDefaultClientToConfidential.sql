-- Update default client to be confidential (BFF pattern)
-- The Angular UI now uses Quarkus backend as BFF, making it a confidential client

UPDATE T_oauth_clients 
SET 
    client_type = 'confidential',
    -- BCrypt hash of 'dev-secret-CHANGE-IN-PROD' with cost factor 10
    -- This is a valid BCrypt hash for development use
    -- In production, this MUST be changed via admin UI or direct database update
    client_secret_hash = '$2a$10$mtwoJ4E6V6XPY8DHrKEpIuV2n0Q1J7FjMZkja5Kv0lYkq36LxcZdO'
WHERE client_id = 'abstratium-abstrauth';

-- IMPORTANT: In production, the client secret must be:
-- 1. Generated securely (e.g., using a password manager or crypto.randomBytes)
-- 2. Set in environment variable ABSTRAUTH_CLIENT_SECRET
-- 3. The hash in this table should match that secret
-- 4. Consider adding admin UI to reset client secrets
