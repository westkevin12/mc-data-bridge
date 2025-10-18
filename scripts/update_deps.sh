#!/bin/bash

# Exit on error
set -e

# 1. Check for updates
echo "Checking for dependency updates..."
mvn org.codehaus.mojo:versions-maven-plugin:2.15.0:display-dependency-updates

# 2. Update the pom.xml
echo "Updating pom.xml with the latest versions..."
mvn org.codehaus.mojo:versions-maven-plugin:2.15.0:use-latest-versions -DgenerateBackupPoms=false

# 3. Build the project
echo "Building the project with new dependencies..."
if mvn clean package; then
  echo "Build successful!"
else
  echo "Build failed. Reverting pom.xml..."
  git checkout pom.xml
  echo "pom.xml has been reverted."
  exit 1
fi
