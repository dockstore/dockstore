import psycopg2
import sys
import urllib
import urllib2
import json
import getopt
import io

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

    print "Successfully connected"

    cur.execute("SELECT * FROM information_schema.tables WHERE table_name='dockstore_containers';")
    if not bool(cur.rowcount):
        cur.execute("CREATE TABLE dockstore_containers (id varchar PRIMARY KEY, \
                                              author varchar, \
                                              description varchar, \
                                              labels json, \
                                              users varchar, \
                                              email varchar, \
                                              lastUpdated varchar, \
                                              gitUrl varchar, \
                                              metaVersion varchar);")


if __name__ == '__main__':
    main(sys.argv[1:])
