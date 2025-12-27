# TODO

## Today

- describe the cpu/memory/etc. footprint in README.md


https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps-26#section-6.3.4.3 :
This architecture is not recommended for business applications, sensitive applications, and applications that handle personal data.


=> we need a BFF

    Wait, I just realised something. When the authorization server responds to the consent request, it redirects the browser back to my application using http 302. the url contains the code and state. the browser then makes the request to the application server. 

    so in all cases, the browser has the code and state.

    are you saying that it is still safer for the bff to do the token exchange than it is for the client to do it, because if javascript were say injected by a dependency, that javascript would have access to the code and state. but in the case of using a bff, the browser doesn't have access to things like the code verifier and will never see the token, so it is safer for that reason?


make the abstrauth client create a session. document the decision. state that it is more correct to create a session, rather than push the token into the client.  or at least have an api that turns the token that is received, into a cookie.

update the client example to use the cookie instead of leaking the token to the ui. or is that already done?

update arch decisions for BFFs, if i decide that the token can be a cookie.

- `auth-callback.component.ts` should fetch the http only cookie from the server, since it cannot set it to http only itself. see `document.cookie` => actually simply make `POST /oauth2/token` return the cookie as http only and secure if prod
see https://quarkus.io/guides/security-jwt#:~:text=issued%20at)%20time.-,mp.jwt.token.header,-Authorization

# Tell Quarkus to look for JWT in Cookie header instead of Authorization
mp.jwt.token.header=Cookie

# Specify the cookie name containing the JWT
mp.jwt.token.cookie=access_token

- use https://quarkus.io/guides/security-oidc-code-flow-authentication#proof-key-for-code-exchange-pkce for other applications - see pkce section. 
- refactor all ErrorResponse classes into a common class with its own file
- docs - describe how to run as a docker image
- docs - link to native build and other docs
- improve branch coverage of ui
- use         @TestSecurity(user = "admin", roles = {"abstratium-abstrauth_admin"})
 in a test somewhere
- if native sign in is disabled, don't add the option to create an account with native sign in
- make it so that you cannot add roles to users who have never signed in, as it is a security issue as mentioned in [USER_GUIDE.md](USER_GUIDE.md). once this has been supressed, describe it in the manual.
  - actually make it only possible once they have changed their password, if native.
  - split the link into two - one is a link the second is a password. only if the password matches, will it work. password also required for non-native users. the point being that you can transfer the two using different mediums. the pasword should be simple.
- allow to disable sign in with native - needs a test
- entire build cycle including native
- review tests
- complete other open points from first security audit
- security audit for CRUD operations on clients and accounts "using any trick in the book" for accessing the server via its web API (no direct access of the database, no access to the file system) - try to CRUD accounts, clients and roles!


## Tomorrow

- csrf cookie for normal use? gitea seems to have one
- track ip address and browser info. if a new sign in is detected, inform the user via email
- MFA
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
