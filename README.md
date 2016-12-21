# Cask Marketplace

## Directory Structure

The marketplace repository requires this directory structure:

    packages/<name>/<version>/spec.json
    packages/<name>/<version>/icon.jpg
    packages/<name>/<version>/license.txt
    packages/<name>/<version>/<other files>

Anything that falls under 'other files' will be zipped up by the packager into an 'archive.zip' file.

## Packager

To build the packager:

    cd packager
    mvn clean package

The packager can be used to create the packages.json catalog file, create 'archive.zip'
files, sign package specs and archives, and push the package files to s3. You can see the
help manual by running:

    java -cp packager/target/*:packager/target/lib/* co.cask.marketplace.Tool
  
The packager uses PGP to sign archives and specs. It is therefore compatible with keyrings
created with GnuPG. For example:

    java -cp packager/target/*:packager/target/lib/* co.cask.marketplace.Tool build -k ~/.gnupg/secring.gpg -i 499BC990789824FD -p mypassword 

This will go through the files under the 'packages' directory, adding a 'packages.json'
catalog at the top level and adding 'spec.json.asc', 'archive.zip', and 'archive.zip.asc' files:

    packages.json
    packages/<name>/<version>/spec.json
    packages/<name>/<version>/spec.json.asc
    packages/<name>/<version>/icon.jpg
    packages/<name>/<version>/license.txt
    packages/<name>/<version>/<file1>
    packages/<name>/<version>/<file1>.asc
    packages/<name>/<version>/<file2>
    packages/<name>/<version>/<file2>.asc

To build all the packages and also push them to s3:

    java -cp packager/target/*:packager/target/lib/* co.cask.marketplace.Tool publish -k <gpg keyring file> -i <keyid> -p <key password> -s3b <s3 bucket> -s3a <s3 access key> -s3s <s3 secret key>

This will build and sign all packages, as well as push anything that has changed to s3.
The tool will use the md5 and file size to determine whether an object has changed or not.
Signatures will only be pushed if the corresponding file has changed.

## Package specs

The spec.json file must be a JSON Object with this format:

    
    {
      "specVersion": "1.0",
      "label": "<label>",
      "description": "<description>",
      "author": "<author>",
      "org": "<org>",
      "created": <timestamp in seconds>,
      "categories": [ <categories> ],
      "cdapVersion": "<cdap version range>" (for example: "[4.0.0,4.1.0)")
      "actions": [
        {
          "type": "create_stream" | "create_app" | "create_artifact" | "create_dataset" | "load_datapack",
          "arguments": [
            {
              "name": "<arg name>",
              "value": <arg value>
            },
            ...
          ]
        },
        ...
      ]
    }

