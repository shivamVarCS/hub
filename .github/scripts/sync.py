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

import utilities
import os
import logging
import json

logging.getLogger().setLevel(logging.DEBUG)    # Enable logging in GitHub Workflow

central_bucket, regional_buckets = utilities.get_bucket_list()

jsonStr = ''
for bucket in regional_buckets:
    jsonStr += '{\"central_address\":\"%s\",\"regional_address\":\"%s\"},' %('gs://' + central_bucket, 'gs://' + bucket + '/hub')

output = '[' + jsonStr[:-1] + ']'
logging.debug('Output of list.py: ')
logging.debug(json.dumps(json.loads(output), indent=2))    # Pretty print JSON output

env_file = os.getenv('GITHUB_ENV')
with open(env_file, "a") as myfile:
    myfile.write("buckets=" + str(output))

# commands to sync the local repo in the runner to GCS central production bucket
# -c flag : to compute and compare checksums (instead of comparing mtime) for files

utilities.run_shell_command('gsutil -m rsync -d -c -r -n packages/ gs://' + central_bucket + '/packages/')
utilities.run_shell_command('gsutil cp -n categories.json gs://' + central_bucket + '/categories.json')
utilities.run_shell_command('gsutil cp -n packages.json gs://' + central_bucket + '/packages.json')