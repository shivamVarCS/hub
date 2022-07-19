# Copyright © 2022 Cask Data, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at

# http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import json
import os
import logging
import utilities

logging.getLogger().setLevel(logging.DEBUG)    # Enable logging in GitHub Workflow

toFetch, ids = utilities.get_missing_files()

logging.info('Missing files before retrieval are: ')
for file in toFetch:
    logging.info(file)

logging.info('Maven GroupID:ArtifactID:Version of corresponding missing files are: ')
for id in ids:
    logging.info(id)

jsonStr = ''
for i in range(len(toFetch)):
    extension = toFetch[i].split('.')[-1]
    jsonStr += '{\"path\":\"%s\",\"target_path\":\"artifact/%s\",\"artifact\":\"%s\",\"artifactDir\":\"%s\",\"repo\":{\"id\":\"%s\",\"file_type\":\"%s\"}},' %(toFetch[i], toFetch[i].split('/')[3], toFetch[i].split('/')[3], toFetch[i].rsplit('/', 1)[0], ids[i], extension)

output = '[' + jsonStr[:-1] + ']'
logging.debug('Output of list.py: ')
logging.debug(json.dumps(json.loads(output), indent=2))    # Pretty print JSON output

# Example Output:
# [
#       {
#           "path": "packages/hydrator-plugin-gcp-plugins/0.15.2/google-cloud-0.15.2.jar",
#           "target_path": "artifact/google-cloud-0.15.2.jar",
#           "artifact": "google-cloud-0.15.2.jar",
#           "artifactDir": "packages/hydrator-plugin-gcp-plugins/0.15.2",
#           "repo": {
#                 "id": "io.cdap.plugin:google-cloud:0.15.2",
#                 "file_type": "jar"
#           }
#     }
# ]

# Set output as environment variable of GitHub workflow runner
env_file = os.getenv('GITHUB_ENV')
with open(env_file, "a") as myfile:
    myfile.write("output=" + str(output))