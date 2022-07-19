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

import os
import shutil
import logging
import utilities

logging.getLogger().setLevel(logging.DEBUG)    # Enable logging in GitHub Workflow

toFetch, ids = utilities.get_missing_files()

# Every missing file downloaded in ./artifact directory is placed in appropriate location
for file in toFetch:
    fileName = file.split('/')[3]
    logging.debug('Merging missing file: ' + fileName)
    if(os.path.isfile(os.path.join('artifacts', fileName, fileName))):
        shutil.move(os.path.join('artifacts', fileName, fileName), file)
    else:
        logging.warning(file + ' : not retrieved')

shutil.move(os.path.join('artifacts', 'packages.json', 'packages.json'), 'packages.json')

if(os.path.isdir('artifacts')):
    shutil.rmtree('artifacts')

toFetch, ids = utilities.get_missing_files()

logging.info('Missing files after retrieval: ')
for file in toFetch:
    logging.info(file)

assert len(toFetch) == 0, 'Some artifact files are still missing'
