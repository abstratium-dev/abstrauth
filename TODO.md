# TODO

## Today

- does http://localhost:8080/oauth2/authorize/details/ show a list of ALL of the urls IN PROD? coz that'd be bad
- create a better description of the project. is it also openid and also an idp or iam?
- restructure docs - e.g. add most MD files to the docs folder. the main readme should focus on selling the application as a docker image. other stuff should be in the docs folder.
- disallow deleting last admin account
- make it impossible to delete the client abstratium-abstrauth
- improve branch coverage of ui
- if native sign in is disabled, don't add the option to create an account with native sign in
- allow to disable sign in with native - needs a test
- e2e
  - add scripts for clearing database
  - one test for with registration, one for without
  - function for logging in as admin and normal
- entire build cycle including native
- make e2e add an admin user, and use that through out multiple tests in a single file
- review tests
- e2e tests for
  - CUD roles
  - CUD clients
  - CUD accounts
- complete other open points from first security audit
- security audit for CRUD operations on clients and accounts "using any trick in the book" for accessing the server via its web API (no direct access of the database, no access to the file system) - try to CRUD accounts, clients and roles!


## Tomorrow

- make fields on create-account-form readonly after creating the account until the user closes the form with the done button. or simply hide them and show the email name and authprovide in the success message.
- scheduler to clear out old authorization requests and authorization codes
- does using testtransaction really make sense - is it a good practice?
  - replace with normal transactional, and make data in each test unique
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
- add using refresh tokens
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
