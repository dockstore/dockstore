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

    # Check whether need to create new gmod_tools table
    cur.execute("SELECT * FROM information_schema.tables WHERE table_name='gmod_tools';")
    if not bool(cur.rowcount):
        cur.execute("CREATE TABLE gmod_tools (globalId varchar PRIMARY KEY, \
                                              registryId varchar, \
                                              registry varchar, \
                                              tooltype json, \
                                              name varchar, \
                                              organization varchar, \
                                              description varchar, \
                                              author varchar, \
                                              metaVersion varchar);")

    # check whether need to create new gmod_tools_versions_table
    cur.execute("SELECT * FROM information_schema.tables WHERE table_name='gmod_tools_versions_table'")
    if not bool(cur.rowcount):
        cur.execute("CREATE TABLE gmod_tools_versions_table (name varchar, \
                                              globalId varchar PRIMARY KEY, \
                                              registryId varchar, \
                                              image varchar, \
                                              descriptor json, \
                                              dockerfile json, \
                                              metaVersion varchar);")

    request = urllib2.Request('https://www.dockstore.org:8443/api/v1/tools')
    response = urllib2.urlopen(request)
    json_data = json.loads(response.read())
    # with open('./scriptTests/jsonoriginal.json', 'w') as outfile:
    #     json.dump(reveieve_json[:], outfile, indent = 4)

    # transfer the fetched jsonfile as array
    # json_data = None
    # with open('./scriptTests/jsonafteradd.json') as infile:
    #     json_data = json.load(infile)

    json_data_globalIdSet = []
    json_data_versionGlobalIdSet = []
    for item in json_data:
        json_data_globalIdSet.append(str(item["global-id"]))
        for version in item["versions"]:
            json_data_versionGlobalIdSet.append(str(version["global-id"]))

    print "# of JSON's tools", len(json_data_globalIdSet)
    print "# of JSON's versions", len(json_data_versionGlobalIdSet)

    # transfer the table data into array
    cur.execute("SELECT globalId FROM gmod_tools;")
    database_globalIds = cur.fetchall()
    database_globalIdSet = []
    for item in database_globalIds:
        database_globalIdSet.append(item[0])
    print "# of tools in present db", len(database_globalIdSet)

    # get data from gmod_tools_versions_table's globalId into array
    cur.execute("SELECT globalId FROM gmod_tools_versions_table;")
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
        cur.execute("DELETE FROM gmod_tools WHERE globalId = %s;", (item,))

    for item in plan_remove_version_items:
        cur.execute("DELETE FROM gmod_tools_versions_table WHERE globalId = %s;", (item,))

    for item in json_data:
        if item["global-id"] in plan_add_items:
            cur.execute("INSERT INTO gmod_tools (globalId, registryId, registry, tooltype, \
                         name, organization, description, author, metaVersion) VALUES \
                         (%s, %s, %s, %s, %s, %s, %s, %s, %s);", (item["global-id"], \
                         item["registry-id"], item["registry"], json.dumps(item["tooltype"]), \
                         item["name"], item["organization"], \
                         item["description"], item["author"], \
                         item["meta-version"]))

            for version in item["versions"]:
                cur.execute("INSERT INTO gmod_tools_versions_table (name, globalId, registryId, \
                             image, descriptor, dockerfile, metaVersion) VALUES \
                             (%s, %s, %s, %s, %s, %s, %s);", (version["name"], version["global-id"], \
                             version["registry-id"], version["image"], json.dumps(version["descriptor"]), \
                             json.dumps(version["dockerfile"]), version["meta-version"]))

    cur.execute("SELECT name FROM gmod_tools_versions_table;")
    versionTable = cur.fetchall()

    cur.execute("SELECT name FROM gmod_tools;")
    toolTable = cur.fetchall()

    print "# of tools in final db", len(toolTable)
    print "# of version in final db", len(versionTable)

    conn.commit()
    cur.close()
    conn.close()

if __name__ == '__main__':
    main(sys.argv[1:])
