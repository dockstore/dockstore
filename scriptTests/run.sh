echo "Starting Postgres"
bash /docker-entrypoint.sh postgres &
sleep 10

# todo put the web service startup here
# echo "Starting Java Web Service"
psql -c "create user ulim with password '3233173' createdb;" -U postgres
psql -c "ALTER USER ulim WITH superuser;" -U postgres
psql -c 'create database dockstore with owner = ulim;' -U postgres


# psql -c "create user <username> with password '<password>' createdb;" -U postgres
# psql -c "ALTER USER <username> WITH superuser;" -U postgres
# psql -c 'create database <dbname> with owner = <username>;' -U postgres

python fetchDataFromDockstore.py
python sever.py
