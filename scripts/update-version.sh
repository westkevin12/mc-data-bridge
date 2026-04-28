#!/bin/bash
set -e

# Colors for pretty output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}      MC Data Bridge - Release Prep Tool       ${NC}"
echo -e "${BLUE}===============================================${NC}"

# Files to update
POM_FILE="pom.xml"
PLUGIN_YML="src/main/resources/plugin.yml"
BUNGEE_YML="src/main/resources/bungee.yml"
VELOCITY_JSON="src/main/resources/velocity-plugin.json"
VELOCITY_JAVA="src/main/java/com/digitalserverhost/plugins/proxy/velocity/VelocityMCDataBridge.java"
WATERFALL_YML="src/main/resources/waterfall.yml"
PURPUR_YML="src/main/resources/purpur.yml"

# 1. Get Current Version from Maven
echo -e "${YELLOW}Reading current version...${NC}"
CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

# Strip -SNAPSHOT if present for calculation purposes
BASE_VERSION=${CURRENT_VERSION%-SNAPSHOT}

# Parse semver (X.Y.Z)
IFS='.' read -r -a VERSION_PARTS <<< "$BASE_VERSION"
MAJOR="${VERSION_PARTS[0]}"
MINOR="${VERSION_PARTS[1]}"
PATCH="${VERSION_PARTS[2]}"

# Calculate options
NEXT_MAJOR="$((MAJOR + 1)).0.0"
NEXT_MINOR="$MAJOR.$((MINOR + 1)).0"
NEXT_PATCH="$MAJOR.$MINOR.$((PATCH + 1))"

echo -e "Current Version: ${GREEN}$CURRENT_VERSION${NC}"
echo ""
echo "Select update type:"
echo "1) Major ($NEXT_MAJOR)"
echo "2) Minor ($NEXT_MINOR)"
echo "3) Patch ($NEXT_PATCH)"
echo "4) Manual Entry"
echo "5) Release Current Snapshot ($BASE_VERSION)"

read -p "Enter choice [1-5]: " CHOICE

case $CHOICE in
    1) NEW_VERSION=$NEXT_MAJOR ;;
    2) NEW_VERSION=$NEXT_MINOR ;;
    3) NEW_VERSION=$NEXT_PATCH ;;
    4) 
        read -p "Enter new version: " MANUAL_VERSION
        NEW_VERSION=$MANUAL_VERSION
        ;;
    5) NEW_VERSION=$BASE_VERSION ;;
    *) echo -e "${RED}Invalid choice.${NC}"; exit 1 ;;
esac

echo ""
echo -e "Target Version: ${GREEN}$NEW_VERSION${NC}"
read -p "Apply this version to all files? (y/n) " CONFIRM
if [[ $CONFIRM != "y" ]]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo -e "${YELLOW}Updating files...${NC}"

# Update pom.xml
echo "Updating $POM_FILE..."
mvn versions:set -DnewVersion="$NEW_VERSION" -DgenerateBackupPoms=false > /dev/null

# Update plugin.yml
echo "Updating $PLUGIN_YML..."
# Use regex to find "version: anything" and replace it
# Handles potential quotes around the version
sed -i "s/^version: .*/version: $NEW_VERSION/" "$PLUGIN_YML"

# Update bungee.yml
echo "Updating $BUNGEE_YML..."
sed -i "s/^version: .*/version: $NEW_VERSION/" "$BUNGEE_YML"

# Update velocity-plugin.json
if [ -f "$VELOCITY_JSON" ]; then
    echo "Updating $VELOCITY_JSON..."
    sed -i "s/\"version\": \".*\"/\"version\": \"$NEW_VERSION\"/" "$VELOCITY_JSON"
fi

# Update VelocityMCDataBridge.java
if [ -f "$VELOCITY_JAVA" ]; then
    echo "Updating $VELOCITY_JAVA..."
    sed -i "s/version = \".*\"/version = \"$NEW_VERSION\"/" "$VELOCITY_JAVA"
fi

# Update waterfall.yml
if [ -f "$WATERFALL_YML" ]; then
    echo "Updating $WATERFALL_YML..."
    sed -i "s/^version: .*/version: $NEW_VERSION/" "$WATERFALL_YML"
fi

# Update purpur.yml
if [ -f "$PURPUR_YML" ]; then
    echo "Updating $PURPUR_YML..."
    sed -i "s/^version: .*/version: $NEW_VERSION/" "$PURPUR_YML"
fi

echo ""
echo -e "${GREEN}Success! Version updated to $NEW_VERSION.${NC}"
echo -e "${RED}IMPORTANT: You must now manually update release-notes.md!${NC}"
echo "We explicitly did NOT update it so you can verify the release content."
