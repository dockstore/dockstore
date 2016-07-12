import psycopg2
import sys
import urllib
import urllib2
import json
import getopt
import io

def main():
    # set the configuration from command line for database
    # databaseArg = ''
    # databaseUserArg = ''
    # databasePasswordArg = ''
    #
    # try:
    #     opts, args = getopt.getopt(argv, "d:u:p:")
    # except getopt.GetoptError:
    #     print 'usage'
    #     sys.exit(2)
    #
    # for opt, arg in opts:
    #     if opt == '-d':
    #         databaseArg = arg
    #     elif opt == '-u':
    #         databaseUserArg = arg
    #     elif opt == '-p':
    #         databasePasswordArg = arg

    # configure and connect database
    # conn_string = "host='localhost' dbname='" + databaseArg + "' user='" + databaseUserArg + \
    #               "' password='"+ databasePasswordArg +"'"
    conn_string = "host='localhost' dbname='dockstore' user='ulim' password='3233173'"
    conn = psycopg2.connect(conn_string)
    cur = conn.cursor()

    # Check whether need to create new gmod_tools table
    cur.execute("SELECT * FROM information_schema.tables WHERE table_name='gmod_tools';")
    if not bool(cur.rowcount):
        cur.execute("CREATE TABLE gmod_tools (url varchar PRIMARY KEY, \
                                              id varchar, \
                                              organization varchar, \
                                              toolname varchar, \
                                              tooltype json, \
                                              description varchar, \
                                              author varchar, \
                                              metaVersion varchar);")

    # check whether need to create new gmod_tools_versions_table
    cur.execute("SELECT * FROM information_schema.tables WHERE table_name='gmod_tools_versions_table'")
    if not bool(cur.rowcount):
        cur.execute("CREATE TABLE gmod_tools_versions_table (name varchar, \
                                              url varchar PRIMARY KEY, \
                                              id varchar, \
                                              image varchar, \
                                              descriptor json, \
                                              dockerfile json, \
                                              metaVersion varchar);")

    request = urllib2.Request('https://www.dockstore.org:8443/api/v1/tools')
    response = urllib2.urlopen(request)
    json_data = json.loads(response.read())

    json_data_globalIdSet = []
    json_data_versionGlobalIdSet = []
    for item in json_data:
        json_data_globalIdSet.append(str(item["url"]))
        for version in item["versions"]:
            json_data_versionGlobalIdSet.append(str(version["url"]))

    print "# of JSON's tools", len(json_data_globalIdSet)
    print "# of JSON's versions", len(json_data_versionGlobalIdSet)

    # transfer the table data into array
    cur.execute("SELECT url FROM gmod_tools;")
    database_globalIds = cur.fetchall()
    database_globalIdSet = []
    for item in database_globalIds:
        database_globalIdSet.append(item[0])
    print "# of tools in present db", len(database_globalIdSet)

    # get data from gmod_tools_versions_table's globalId into array
    cur.execute("SELECT url FROM gmod_tools_versions_table;")
    database_versionGlobalIds = cur.fetchall()
    database_versionGlobalSet = []
    for item in database_versionGlobalIds:
        database_versionGlobalSet.append(item[0])
    print "# of versions in present db", len(database_versionGlobalSet)

    plan_add_items =  list(set(json_data_globalIdSet) - set(database_globalIdSet))
    plan_remove_items = list(set(database_globalIdSet) - set(json_data_globalIdSet))

    plan_add_version_items = list(set(json_data_versionGlobalIdSet) - set(database_versionGlobalSet))
    plan_remove_version_items = list(set(database_versionGlobalSet) - set(json_data_versionGlobalIdSet))

    # remove and add element form gmod_tools and gmod_tools_versions_table
    for item in plan_remove_items:
        # gmod_tools
        cur.execute("DELETE FROM gmod_tools WHERE url = %s;", (item,))

    for item in plan_remove_version_items:
        cur.execute("DELETE FROM gmod_tools_versions_table WHERE url = %s;", (item,))

    for item in json_data:
        if item["url"] in plan_add_items:
            cur.execute("INSERT INTO gmod_tools (url, id, organization, toolname, tooltype, \
                         description, author, metaVersion) VALUES \
                         (%s, %s, %s, %s, %s, %s, %s, %s);", (item["url"], \
                         item["id"], item["organization"], item["toolname"], json.dumps(item["tooltype"]), \
                         item["description"], item["author"], item["meta-version"]))

            for version in item["versions"]:
                cur.execute("INSERT INTO gmod_tools_versions_table (name, url, id, image, \
                             descriptor, dockerfile, metaVersion) VALUES \
                             (%s, %s, %s, %s, %s, %s, %s);", (version["name"], version["url"], \
                             version["id"], version["image"], json.dumps(version["descriptor"]), \
                             json.dumps(version["dockerfile"]), version["meta-version"]))

    cur.execute("SELECT name FROM gmod_tools_versions_table;")
    versionTable = cur.fetchall()

    cur.execute("SELECT url FROM gmod_tools;")
    toolTable = cur.fetchall()

    print "# of tools in final db", len(toolTable)
    print "# of version in final db", len(versionTable)

    conn.commit()
    cur.close()
    conn.close()

if __name__ == '__main__':
    main()
