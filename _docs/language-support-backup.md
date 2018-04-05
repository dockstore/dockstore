/*
 *     Copyright 2018 OICR
 *
 *     Licensed under the Apache License, Version 2.0 (the "License")
 *     you may not use this file except in compliance with the License
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
Sample markdown table

| Feature                | CWL           | WDL   |
| ---------------------  | ------------- | ----- | 
| **GUI**                |               |       |
| Tool registration      | ✔             | ✔     | 
| Workflow registration  | ✔             | ✔     |  
| DAG Display            | ✔             | ✔     |  
| Launch-with Platforms  | N/A           | FireCloud<br>DNAStack |  
| **CLI**                |               |       |
| File Provisioning      | Local File System<br>HTTP<br>FTP<br>S3             | Local File System     | 
| Local workflow engines | cwltool<br>Bunny<br>Toil (experimental)              | Cromwell* |
{: .table .table-striped .table-condensed}
