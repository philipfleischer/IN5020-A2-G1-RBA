#!/bin/sh
#
# Builds the two ready-to-deploy jar files:
#   jars/MessageDeliveryServer.jar
#   jars/BankServer.jar
#
# Usage (macOS / Linux), from the project folder:
#   ./scripts/build.sh
#
# Needs a JDK 11 or newer (javac and jar on the PATH).

# Stop at the first command that fails
set -e

# Run from the project folder, wherever the script is called from
cd "$(dirname "$0")/.."

rm -rf out jars
mkdir -p out jars

# --release 11: the jars run on any Java 11 or newer, even when they are
# built with a newer JDK
javac --release 11 -d out $(find src -name '*.java')

# Both jars contain all classes: the bank server needs the Message
# Delivery server's remote interface and vice versa. Only the class that
# "java -jar" starts (the Main-Class) is different.
jar --create --file jars/MessageDeliveryServer.jar \
    --main-class src.message_delivery_server.MessageDeliveryServer -C out .

jar --create --file jars/BankServer.jar \
    --main-class src.bank_server.BankServer -C out .

echo "Built jars/MessageDeliveryServer.jar and jars/BankServer.jar"
