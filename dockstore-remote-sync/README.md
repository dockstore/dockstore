Fetch Data from
-----
### Introduction
This work allows users to type in the local database information so as to fetch data from remote website and store the data into database. Then start the serve to realize search.

### Install locally
#### Configuration
- Login to posgreSQL `psql -U postgres -h localhost`
- Create user and database to store the data fetched from dockstore
  - `CREATE ROLE <username> LOGIN PASSWORD '<password>'`
  - `CREATE DATABASE <databasename> OWNER <username>`

#### Fetch data remotely and store in local database
`python fetchDataFromDockstore.py -d <databasename> -u <username> -p <password>`

#### start the URL API server, for
`python server.py`


### Run on docker
#### Get the Repository
`git clone git@github.com:spacime/dockstore_search_serve.git`
#### Build Image Based ont Dockerfile
`sudo docker build -t <name of image> .`
#### Start the Created Image
`sudo docker run <name of image>`
#### Configure the Port
`sudo docker run -d -p <port of local machine>:<port of image> fetch_data_server`
