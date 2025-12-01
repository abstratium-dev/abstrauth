# Naming Conventions

## Client naming

Clients are named using the component name, as described in the wiki (see Backstage), e.g. `abstratium-abstrauth`.

## Role naming

Roles are named using the following convention:

<client_id>_<role_name>

Where <client_id> is the client_id of the client (see above) and <role_name> is the name of the role.

The role should consist of a [verb and a noun to describe what a user can do to the noun](https://en.wikipedia.org/wiki/Apollo_Guidance_Computer#DSKY_interface), e.g. `read_users` or `write_users`.

Roles should not build up upon other roles, e.g. `read_write_users` should not exist.

Roles should be named in snake_case.
