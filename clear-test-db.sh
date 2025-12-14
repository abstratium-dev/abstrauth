#!/bin/bash
docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -psecret mysql -e "DELETE FROM abstrauth.T_accounts;"
docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -psecret mysql -e "DELETE FROM abstrauth.T_oauth_clients where client_id <> 'abstratium-abstrauth';"
