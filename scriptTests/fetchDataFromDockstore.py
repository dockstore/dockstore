import psycopg2
import sys
import urllib
import urllib2
import json
import getopt

def main(argv):
    # set the configuration from command line for database
    databaseArg = ''
    databaseUserArg = ''
    databasePasswordArg = ''
    try:
        opts, args = getopt.getopt(argv, "d:u:p:")
    except getopt.GetoptError:
        print 'usage'
        sys.exit(2)

    for opt, arg in opts:
        if opt == '-d':
            databaseArg = arg
        elif opt == '-u':
            databaseUserArg = arg
        elif opt == '-p':
            databasePasswordArg = arg


    # configure and connect database
    conn_string = "host='localhost' dbname='" + databaseArg + "' user='" + databaseUserArg + \
                  "' password='"+ databasePasswordArg +"'"
    conn = psycopg2.connect(conn_string)
    cur = conn.cursor()

    cur.execute("SELECT * FROM information_schema.tables WHERE table_name='gmod_tools'")
    if bool(cur.rowcount):
        cur.execute("DROP TABLE gmod_tools");

    cur.execute("CREATE TABLE gmod_tools (id serial PRIMARY KEY, \
                                          globalId varchar, \
                                          registryId varchar, \
                                          registry varchar, \
                                          name varchar, \
                                          organization varchar, \
                                          description varchar, \
                                          author varchar, \
                                          metaVersion varchar);")


    request = urllib2.Request('https://www.dockstore.org:8443/api/v1/tools')
    response = urllib2.urlopen(request)
    reveieve_json = json.loads(response.read())

    for item in reveieve_json:
        cur.execute("INSERT INTO gmod_tools (globalId, registryId, registry, \
                     name, organization, description, author, metaVersion) VALUES \
                     (%s, %s, %s, %s, %s, %s, %s, %s);", (item["global-id"], \
                     item["registry-id"], item["registry"], \
                     item["name"], item["organization"], \
                     item["description"], item["author"], \
                     item["meta-version"]))

    # update the database
    conn.commit();
    cur.close()
    conn.close()

if __name__ == '__main__':
    main(sys.argv[1:])
