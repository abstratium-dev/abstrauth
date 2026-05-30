# TODO

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes


## Today

- multi-tenancy
  - check all statements never do bulk operations
    - copy md from abstoggle which explains the rules
    - also put that doc in to abstracore
  - review security
  - auto subscription. what if a user is already associated with multiple orgs, are all new subscribers? wait, they subscribe t o the clientId in the request. but it could be wrong for all of their orgs to subscribe. so only the one that they CHOSE during sign in should subscribe! so auto subscribe happens after they choose the org that they are signing in under
  - create security tests to make sure the service cannot be manipulated into providing a token with a wrong orgId
  - define (complex) test cases for where the user is added to a different organisation, etc.
  - `ADMIN` role is org-scoped only — admins cannot see accounts/clients across orgs. Add a `platform-admin` role (or equivalent) with cross-org read/write access for true platform administration. See `docs/ephemeral-and-volatile-and-temporary-but-interesting/ADMIN_ROLE_LIMITATIONS.md`.

- introduce envers

- add public page to abstrauth WITH ™ symbol!
  - in list of things which abstrauth is, describe it is also an "identity broker" as written in microsoft doc

- add legal page to abstrauth

- change concept of clients having secrets. where did i get that idea?? search the code.

- X check emails are sent when client secrets are running out -> update 20260511 this isn't implemented. since SecretExpirationNotificationService is empty!!! see SERVICE_TO_SERVICE_AUTH.md which shows a scheduler.
DO NOT SEND EMAIL TO noreply ACCOUNT AS IT ISN'T FORWARDED AND SHOUDLN'T BE!! 
SEARCH for all uses of noreply since we shouldn't send emails there as it isnt monitored.

- what is this log? [io.qua.oid.run.OidcRecorder] (vert.x-eventloop-thread-2) [skey:] Session age extension will not be effective because 'quarkus.oidc.token.refresh-expired=true' is not set

- need to allow other addresses to call management address 9002 in order to get metrics. need to expose it in docker file? 

- when i have no roles for abstracore, i get an error, but it shows abstratium-abstrauth as the client-id, rather than that which is probably in the request object in the db? 

- make it configurable per client, if it allows redirection back to callback if user has no roles, with default false. so that users can be added by the third party, e.g. when creating a shopping cart account. or... make it so that they HAVE to add a role for their client_id? maybe makes more sense.

- make it so that you cannot add roles to users who have never signed in, as it is a security issue as mentioned in [USER_GUIDE.md](USER_GUIDE.md). once this has been supressed, describe it in the manual.
  - actually make it only possible once they have changed their password, if native.
  - split the link into two - one is a link the second is a password. only if the password matches, will it work. password also required for non-native users. the point being that you can transfer the two using different mediums. the pasword should be simple.
- complete other open points from first security audit
- security audit for CRUD operations on clients and accounts "using any trick in the book" for accessing the server via its web API (no direct access of the database, no access to the file system) - try to CRUD accounts, clients and roles!
- review tests

- add more oauth providers:
  - Apple
  - Github


## Tomorrow

- organisations can set domain names, to allow anyone with that domain name to automatically be part of the org when they sign in, by creating a TXT dns record which we read regularly to verify that they really own that domain.

- add the ability for users to request roles and create tasks for managers to approve them. document approval and rejection.
- so that a manager can delegate role management to a subordinate, for a given client_id, we need to add the concept of allowing a user to only add roles for a specific client_id. 
- if native sign in is disabled, don't add the option to create an account with native sign in
- improve branch coverage of ui
- track ip address and browser info. if a new sign in is detected, inform the user via email
- MFA for native
- telemetry
- audit logs
- scheduler to clear out old authorization requests and authorization codes
- need simple INFO logging to show what a user does.
- controller should never return Promises
- angular should always use inject and not constructor
- do we need a revocation check endpoint so that a third party can check that the token
  isn't revoked? it could also check that the token is valid, altho the third party can 
  do that using the public key.
- install in prod and use for hledger
- add description of how to deploy to production
- upload docker image to dockerhub or quay.io?
- does it make sense that revocation is used to log out? in fact, implement log out
- make backend tests nested - account resource and client resource 
- refactor http error handling in angular code, potentially using an interceptor
- add role has a drop down list based on all roles that exist for the given client, searched using the backend, if you have the manage-accounts role. but of course you can also add a new role
- when delete own account, sign straight out

## Later (not yet necessary for initial release)

- mfa for non-native? well google et al. already provide this and ask for mfa if they don't trust that you are already properly authenticated - e.g. try this with an incognito window
- TLS for web endpoints
- TLS for DB connections
- auditing of CUD operations on clients and accounts
- lock and unlock user
- multi-tenancy so that a user with only user management role can only see their own clients and only the users in those clients.
- test client secret support for confidential clients
- native sign in: once scopes are accepted for the client_id, don't keep asking until they change
- what is the point of having a username in native account? delete it, since email suffices. discord doesn't have email - how would that work? it'd be ok, because you just click the sign in with discord link
- support for other databases like postgresql
- account linking
- make build process deploy a test instance and run e2e tests against that and remove all the stuff which maven is doing with the e2e maven profile 
- don't require approval, if client is configured that way and base it on scopes. so user only approves if new scopes are being requested
- sign out - see auth.service.ts
- make aesthetics better and document colours, etc. below
- add optional env vars that allow initial sign in without registration, so that that user has admin role
- show how to make the client verify the signature of the token
- use an enum for the status on authorization requests - see sql 004 for values
- make authorization requests become expired
- state field needs to work if other third party apps want to use it
- clean up authorization requests after a month
- make /q/... stuff run on a management port, separated from actual application
- what are these for?
  - mp.jwt.verify.issuer=https://quarkus.io/issuer
  - smallrye.jwt.new-token.issuer=https://quarkus.io/issuer
- GDPR - allow user to view all of their data
- GDPR - allow user to delete all of their data
- add microsoft login
- add github login
- add using refresh tokens but only for confidential clients
- add a nonce (number used once) to the authorization request
- add "realms" which are used to create multitenancy in a single instance of abstrauth
- make issuer depend on redirect url?
- split redirect urls into own table or just up to 5 cols?
- check db columns and add functionality for them / check that they are used properly
  - T_accounts.email_verified
  - T_authorization_codes.used
  - T_authorization_codes.expires_at
  - T_authorization_requests.status
  - T_authorization_requests.expires_at
  - T_credentials.failed_login_attempts
  - T_credentials.locked_until
- logout (revocation?)
- don't allow multiple credentials for one account (uk on foreign key?)
- don't allow multiple auth requests (or codes?) for same client and account - remove if there are duplicates?
- how to build native
- github build? https://github.com/abstratium-dev/abstrauth/new/main?filename=.github%2Fworkflows%2Fmaven.yml&workflow_template=ci%2Fmaven
  - and then show results in github
- document production setup
- autocomplete: https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#attr-fe-autocomplete-username
- [x] check using https://oauthdebugger.com/
