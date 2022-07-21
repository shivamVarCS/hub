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
import subprocess
import shlex
import json
import yaml
import re
import logging

logging.getLogger().setLevel(logging.DEBUG)    # Enable logging in GitHub Workflow and enable printing of info level logs

class LazyDecoder(json.JSONDecoder):    # Brute force RE based methods to decode JSON into appropriate format
    def decode(self, s, **kwargs):
        regex_replacements = [
              (re.compile(r'([^\\])\\([^\\])'), r'\1\\\\\2'),
              (re.compile(r',(\s*])'), r'\1'),
        ]
        for regex, replacement in regex_replacements:
            s = regex.sub(replacement, s)
        return super().decode(s, **kwargs)

def run_shell_command(command):    # Utility function to run shell commands with appropriate I/O streams
    process = subprocess.Popen(shlex.split(command), stdout=subprocess.PIPE)
    while True:
        output = process.stdout.readline().rstrip().decode('utf-8')
        if output == '' and process.poll() is not None:
            break
        if output:
            print(output.strip())
    rc = process.poll()
    return rc

def get_missing_files():    # Utility function to get list of missing artifact files
    files = []
    ids = []
    packagesDir = 'packages/'
    ignorableActionTypes = ['create_driver_artifact']    # Actions in spec.json for which config files is only logged as warning not error. Eg: hydrator-pipeline-mysql-to-bigquery/1.1.1/mysql-connector-java.json

    for artifact in os.listdir(packagesDir):
        artifactDir = os.path.join(packagesDir, artifact)
        if(os.path.isdir(artifactDir)):
            logging.debug('Checking artifact: ' + artifact)
            for version in os.listdir(artifactDir):
                artifactVersionDir = os.path.join(artifactDir, version)
                if(os.path.isdir(artifactVersionDir)):
                    logging.debug('Checking missing files in ' + artifactVersionDir)
                    if(os.path.isfile(os.path.join(artifactVersionDir, 'spec.json'))):
                        logging.debug('Inspecting spec.json for necessary files')
                        specFile = open(os.path.join(artifactVersionDir, 'spec.json'))
                        specData = json.load(specFile, cls=LazyDecoder)
                        jarFiles = []
                        configFiles = []

                        for object in specData['actions']:
                            for property in object['arguments']:
                                if(property['name'] == 'jar'):
                                    jarFiles.append(property['value'])
                                if(property['name'] == 'config'):
                                    if(object['type'] in ignorableActionTypes):    # Actions in spec.json for which config files is only logged as warning not error. Eg: hydrator-pipeline-mysql-to-bigquery/1.1.1/mysql-connector-java.json
                                        if(not os.path.isfile(os.path.join(artifactVersionDir, property['value']))):
                                            logging.warning(os.path.join(artifactVersionDir, property['value']) + ' not found')
                                    else:
                                        configFiles.append(property['value'])

                        logging.debug('Required files: ')
                        logging.debug(jarFiles)
                        logging.debug(configFiles)

                        for fileList in [jarFiles, configFiles]:
                            for file in fileList:
                                if(not os.path.isfile(os.path.join(artifactVersionDir, file))):
                                    if(os.path.isfile(os.path.join(artifactDir, 'build.yaml'))):
                                        buildFile = open(os.path.join(artifactDir, 'build.yaml'))
                                        buildData = yaml.load(buildFile, Loader=yaml.FullLoader)
                                        groupId = buildData['maven-central']['groupId']
                                        artifactId = buildData['maven-central']['artifactId']
                                        files.append(os.path.join(artifactVersionDir, file))
                                        ids.append('%s:%s:%s' %(groupId, artifactId, version))
                                        logging.debug('Missing file: ' + file)
                                    else:
                                        logging.warning('build.yaml file does not exist for ' + artifactDir)
                                        files.append(os.path.join(artifactVersionDir, file))
                                        ids.append('::%s' %(version))
                    else:
                        logging.error('spec.json does not exist for ' + artifactVersionDir)
                        exit(1)

    return files, ids

def get_bucket_list():

    bucketFile = open(os.path.join('.github', 'scripts', 'gcs-buckets.yaml'))
    bucketData = yaml.load(bucketFile, Loader=yaml.FullLoader)
    central_bucket = bucketData['central-bucket']
    regional_buckets = list(bucketData['regional-buckets'])

    return central_bucket, regional_buckets