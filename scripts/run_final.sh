#!/bin/sh
#
# Runs the official test scenario from input/README.txt and saves the
# output of every process in output/<mode>/:
#
#   t =  0 s: 3 replicas with Rep1.txt
#   t =  7 s: 2 new replicas with Rep2.txt   (late join)
#   t = 12 s: 1 new replica with Rep3.txt    (late join)
#
# Usage (macOS / Linux), from the project folder:
#   ./scripts/run_final.sh [correct|naive] [port]
#
# Takes about 2 minutes. Builds the jars first if they are missing.

set -e
cd "$(dirname "$0")/.."

MODE=${1:-correct}
PORT=${2:-1099}
OUT=output/$MODE
RATES=input/TradingRate.txt

if [ "$MODE" != "correct" ] && [ "$MODE" != "naive" ]; then
    echo "Mode must be 'correct' or 'naive'"
    exit 1
fi

./scripts/build.sh

mkdir -p "$OUT"
rm -f "$OUT"/*.txt

java -jar jars/MessageDeliveryServer.jar "$PORT" > "$OUT/server.txt" 2>&1 &
SERVER=$!
sleep 1

PIDS=""

# bank <command file> <log name>
bank() {
    java -Dsync="$MODE" -jar jars/BankServer.jar "localhost:$PORT" group01 3 \
        "$RATES" "input/$1" > "$OUT/$2.txt" 2>&1 &
    PIDS="$PIDS $!"
    echo "started $2 ($1)"
}

echo "t=0 s"
bank Rep1.txt rep1_a
bank Rep1.txt rep1_b
bank Rep1.txt rep1_c
sleep 7
echo "t=7 s"
bank Rep2.txt rep2_a
bank Rep2.txt rep2_b
sleep 5
echo "t=12 s"
bank Rep3.txt rep3_a

echo "waiting for all replicas to finish (about 2 minutes)..."
for pid in $PIDS; do
    wait "$pid" || true
done

kill "$SERVER" 2>/dev/null || true
echo "done, logs in $OUT/"

./scripts/check_output.sh "$OUT"
