#!/bin/bash

# This script clears the database of user account information

set -o pipefail

psql -h localhost -p 6000 -U horreum-user -W horreum -c "SELECT set_config('horreum.userroles', 'horreum.system' , false);delete from userinfo_teams where username != 'dummy';delete from userinfo_roles where username != 'dummy';delete from userinfo where username != 'dummy';"

