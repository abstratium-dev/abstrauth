use abstrauth;
show tables;
select * from abstrauth.flyway_schema_history;
select * from abstrauth.T_account_roles;
select * from abstrauth.T_accounts;
select * from abstrauth.T_authorization_codes;
select * from abstrauth.T_authorization_requests;
select * from abstrauth.T_client_allowed_roles;
select * from abstrauth.T_credentials;
select * from abstrauth.T_federated_identities;
select * from abstrauth.T_oauth_clients;
select * from abstrauth.T_oauth_client_secrets;
select * from abstrauth.T_organisation_accounts;
select * from abstrauth.T_organisations;
select * from abstrauth.T_revoked_tokens;
select * from abstrauth.T_subscriptions;
select * from abstrauth.T_client_roles;

SELECT * FROM abstrauth.T_oauth_clients WHERE client_id LIKE '%duo%';

-- which roles does an account have? based on T_account_roles. org name, account name, client_id and roles
select o.id as orgId, o.name, a.name, ar.client_id, ar.role from abstrauth.T_account_roles ar join abstrauth.T_organisations o on o.id = ar.org_id join abstrauth.T_accounts a on a.id = ar.account_id;

SELECT count(*) FROM T_oauth_clients WHERE client_id = 'abstratium-abstrauth' AND org_id = 'e67984f8-393b-447c-b917-746f9a8f3c36';

SELECT DISTINCT 
    a.id, a.email, a.email_verified, a.name, a.picture, 
    a.auth_provider, a.created_at,
    ar.id AS role_id, ar.client_id, ar.role, ar.created_at AS role_created
FROM T_accounts a
LEFT JOIN T_account_roles ar ON a.id = ar.account_id
WHERE a.id IN (
    SELECT account_id 
    FROM T_organisation_accounts 
    WHERE org_id = 'e67984f8-393b-447c-b917-746f9a8f3c36' 
      AND role = 'member'
)
ORDER BY a.created_at DESC;