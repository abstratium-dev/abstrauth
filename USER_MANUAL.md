# User Manual

## Installation

It is intended that Abstrauth be run using docker.
Abstrauth supports MySql and will soon also support postgresql and MS SQL Server.

You need to add a database/schema and a user to the database manually. Thereafter, Abstrauth can access that database with that user and it will create all of the necessary tables and an initial OAuth2 client with the client_id `abstratium-abstrauth`. This client is used by the application itself, and users sign into the application using this client in order to manage other clients, as well as accounts and roles.

### Prerequisites

## Initial Onboarding

The first time a user logs in, they are prompted to create an account. This is done to ensure that the user has an account before they can use the service.

The first user is given the admin role so that they can add other accounts if signing up is disabled.

## Account and Role Management

To manage accounts, the user needs the role `abstratium-abstrauth_manage-accounts`.
Users with an account with this role can see all the accounts which have roles mapped to the client_ids that the user is also mapped to.

Users with an account with this role can add and remove roles for accounts. They cannot add the admin role to any account, as only admin users can do that. 
They also cannot add roles to their or other accounts if the client_id of the account they are adding the role to does not already have a role mapped to that client_id.

The idea here is that users can only see other accounts for clients that they have the right to use, and that they can only add roles to users for clients that they have the right to use.

## Client Management

To manage clients, the user needs the role `abstratium-abstrauth_manage-clients`.
Users with an account with this role can see all the clients which the user is mapped to.
