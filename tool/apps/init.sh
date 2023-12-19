#!/usr/bin/env bash

INFO='\033[0;32m[INFO]'
ERR='\033[0;31m[ERR ]'
NC='\033[0m'

SCRIPT_DIR="$(cd $(dirname $0) && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

version=$("${JAVA_HOME}/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo version "$version"
if [[ "$(echo $version | cut -d'.' -f1)" -ge "15" ]]; then
  echo -e "${INFO} Java>=15 found ${NC}"
else
  echo -e "${ERR} Java $version is not supported. Set JAVA_HOME >= 15${NC}"
  exit
fi

(
  cd $PROJECT_DIR
  mvn clean package -DskipTests -DskipITs -V
)

_jar=$(find $SCRIPT_DIR/../synthesizer -name "*-jar-with-dependencies.jar")
jar="$(cd $(dirname $_jar);pwd)/$(basename $_jar)"

echo "export NPETEST_JAVA=${JAVA_HOME}" > $SCRIPT_DIR/configs/bashrc
echo "export NPETEST_JAR=${jar}" >> $SCRIPT_DIR/configs/bashrc
echo "export PATH=${JAVA_HOME}/bin:\${PATH}" >> $SCRIPT_DIR/configs/bashrc
