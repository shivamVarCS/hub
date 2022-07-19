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
import logging
import utilities

logging.getLogger().setLevel(logging.DEBUG)    # Enable logging in GitHub Workflow and enable printing of info level logs

# Initial environment and version check logs
logging.debug('Starting Setup and Build of Packager, packages.json  ....')
logging.debug('Checking environment ....\n')

logging.debug('Java version: ')
utilities.run_shell_command('java -version')

logging.debug('Java compile version: ')
utilities.run_shell_command('javac -version')

logging.debug('Maven version: ')
utilities.run_shell_command('mvn -version')

# Building packager and packages
logging.debug('Building packager ....\n')
os.chdir('./packager/')
utilities.run_shell_command('mvn clean package')
os.chdir('../')

logging.debug('Building packages.json ....\n')
utilities.run_shell_command('java -cp "packager/target/lib/*:packager/target/*" io.cdap.hub.Tool build')

# Listing all files
logging.info('ls:\n', os.listdir())