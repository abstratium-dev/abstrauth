# TODO

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes
- audit that all JPQL and SQL is multi-tenant conform
  - prompt:
    - you are a software expert and know all about hibernate multitenancy using the discriminator approach, as well as envers. see @entity-manager-usage-constraints.md and ensure that the information in that file is adhered to in this project. for example: search for all native sql in `src/main/java` and tell the user that they exists and suggest updates so that JPA queries are used instead. for example: search for all bulk UPDATE and DELETE operations and tell the user that they exist and suggest updates so that non-bulk operations are used instead. you are free to address other potential issues related to using envers and multi-tenancy.


## Today

- when i have no roles for abstracore, i get an error when i sign in, but the error message shows abstratium-abstrauth as the client-id, rather than that which is probably in the request object in the db? 
  - search for "You do not have any roles"

- take flows, security design? and multitenancy design and get rid of stuff related to what has already been implemented (primarily in multitenancy design doc) and create a single spec and design document from that.

- what is this log? [io.qua.oid.run.OidcRecorder] (vert.x-eventloop-thread-2) [skey:] Session age extension will not be effective because 'quarkus.oidc.token.refresh-expired=true' is not set

- complete other open points from first security audit

- security audit for CRUD operations on clients and accounts "using any trick in the book" for accessing the server via its web API (no direct access of the database, no access to the file system) - try to CRUD accounts, clients and roles!

- add more oauth providers:
  - Apple
  - Github


## Tomorrow

- make all tests only reset the db once instead of for all tests.

- need to allow other addresses to call management address 9002 in order to get metrics. need to expose it in docker file? 

- make it so that you cannot add roles to users who have never signed in, as it is a security issue as mentioned in [USER_GUIDE.md](USER_GUIDE.md). once this has been supressed, describe it in the manual.
  - actually make it only possible once they have changed their password, if native.
  - split the link into two - one is a link the second is a password. only if the password matches, will it work. password also required for non-native users. the point being that you can transfer the two using different mediums. the pasword should be simple.

- multi-tenancy: if an org cancels a subscription, then don't delete it, but mark it as logically deleted - that way they can resubscribe and also we won't auto-subscribe the org back if it was public and auto-subscribable, as would be the case if the subscription were simply deleted.

- multi-tenancy - `ADMIN` role is org-scoped only — admins cannot see accounts/clients across orgs, although they could if they used a cross tenant api. CHECK PEOPLE FROM OTHER ORGS CANNOT ADD THEMSELVES AS ADMIN and abuse being able to be and admin. See `docs/ephemeral-and-volatile-and-temporary-but-interesting/ADMIN_ROLE_LIMITATIONS.md`. note that the admin role isn't actually able to do any thing yet

- multi-tenancy: extend MetricsService with orgs, etc.

- multitenancy - can we do all the stuff on the svg diagram and in the design document?

- GDPR - allow user to view all of their data

- GDPR - allow user to delete all of their data

- make deleting accounts and roles (x2) harder -> enter the name to confirm

- Subscription management UI — add ability to subscribe when not auto-subscribe. so use calls to POST /api/organisations/{orgId}/subscriptions or DELETE .../subscriptions/{clientId}.

- inviting user to join your org, the invite should expire and if they don't accept, delete the account that was created.
  - also make it so that the new user has to accept before they are shown in the org - or at least show them as invited but not yet accepted or something.

- organisations can set domain names, to allow anyone with that domain name to automatically be part of the org when they sign in, by creating a TXT dns record which we read regularly to verify that they really own that domain.

- allow org owners who manage subscriptions to turn of auto-subscription on their subscription object (auto-subscription is marked on the client), as a field on the org, so that users cannot just start using any old app. this is a security feature like MS has

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

- what is the point of having a username in native account? delete it, since email suffices.

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

- autocomplete: https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#attr-fe-autocomplete-username

- check using https://oauthdebugger.com/
