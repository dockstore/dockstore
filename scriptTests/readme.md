Fetch Data from
-----
## First work
### Introduction
This work allows users to type in the local database information so as to fetch data from dockstore website and store the data into database.

### Usage
#### Configuration
- Login to posgreSQL `psql -U postgres -h localhost`
- Create user and database to store the data fetched from dockstore
  - `CREATE ROLE <username> LOGIN PASSWORD '<password>'`
  - `CREATE DATABASE <databasename> OWNER <username>`

#### Run locally
`python fetchDataFromDockstore.py -d <databasename> -u <username> -p <password>`
