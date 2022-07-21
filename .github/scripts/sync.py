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
# commands to sync the local repo in the runner to GCS central production bucket
# -c flag : to compute and compare checksums (instead of comparing mtime) for files

utilities.run_shell_command('gsutil -m rsync -d -c -r -n packages/ gs://${CENTRAL_GCS_BUCKET_PREFIX}/packages/')
utilities.run_shell_command('gsutil cp -n categories.json gs://${CENTRAL_GCS_BUCKET_PREFIX}/categories.json')
utilities.run_shell_command('gsutil cp -n packages.json gs://${CENTRAL_GCS_BUCKET_PREFIX}/packages.json')