#!/bin/sh
#
# Shows the ACK timeout: three replicas run input/tests/FailureTest.txt,
# and after 8 s replica C is frozen (kill -STOP), so it is alive but never
# answers. The Message Delivery server resends after 2 s, marks C as FAIL
# after 5 s, and A and B continue with the same balance.
#
# Usage (macOS / Linux), from the project folder:
#   ./scripts/run_failure_demo.sh [port]
#
# Takes about 40 seconds. Logs in output/failure/.

set -e
cd "$(dirname "$0")/.."

PORT=${1:-1099}
OUT=output/failure
CMDS=input/tests/FailureTest.txt

./scripts/build.sh

mkdir -p "$OUT"
rm -f "$OUT"/*.txt

java -jar jars/MessageDeliveryServer.jar "$PORT" > "$OUT/server.txt" 2>&1 &
SERVER=$!
sleep 1

java -jar jars/BankServer.jar "localhost:$PORT" group01 3 input/TradingRate.txt "$CMDS" > "$OUT/replica_a.txt" 2>&1 &
A=$!
java -jar jars/BankServer.jar "localhost:$PORT" group01 3 input/TradingRate.txt "$CMDS" > "$OUT/replica_b.txt" 2>&1 &
B=$!
java -jar jars/BankServer.jar "localhost:$PORT" group01 3 input/TradingRate.txt "$CMDS" > "$OUT/replica_c.txt" 2>&1 &
C=$!

sleep 8
echo "freezing replica C (kill -STOP)"
kill -STOP "$C"

wait "$A" "$B" || true

# C is still frozen: end it, and the server
kill -9 "$C" 2>/dev/null || true
kill "$SERVER" 2>/dev/null || true

echo "== Server: resend and FAIL"
grep -E "resending|FAIL" "$OUT/server.txt" || echo "(no resend/FAIL found)"
echo "== A and B: final balance"
grep -h "Synced balance" "$OUT/replica_a.txt" "$OUT/replica_b.txt"
