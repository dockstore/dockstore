import psycopg2
import json

from flask import Flask


app = Flask(__name__)

@app.route("/", methods=['GET', 'OPTIONS'])
def output_container_format():
    output_container_arr = []
    conn_string = "host='localhost' dbname='dockstore' user='ulim' password='3233173'"
    conn = psycopg2.connect(conn_string)
    cur = conn.cursor();
    cur.execute("SELECT toolname, author, description, url, id FROM gmod_tools")
    containers_info = cur.fetchall()
    for container_info in containers_info:
        info_dict = {}
        info_dict['name'] = container_info[0]
        info_dict['author'] = container_info[1]
        info_dict['gitUrl'] = "git@github.com:ICGC-TCGA-PanCancer/CGP-Somatic-Docker.git"
        info_dict['url'] = container_info[3]
        info_dict['path'] = container_info[4]
        output_container_arr.append(info_dict)

    cur.close()
    conn.close()
    return json.dumps(output_container_arr)

@app.after_request
def after_request(response):
  response.headers.add('Access-Control-Allow-Origin', '*')
  response.headers.add('Access-Control-Allow-Headers', 'Content-Type,Authorization')
  response.headers.add('Access-Control-Allow-Methods', 'GET,PUT,POST,DELETE')
  return response

if __name__ == "__main__":
    app.run(host='0.0.0.0')
