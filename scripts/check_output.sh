#!/bin/sh
#
# Checks the logs written by run_final.sh:
#   1. every replica executed the transactions in the same order
#      (a replica that joined late must have a tail of the others' order)
#   2. all replicas print the same final balance
#
# Usage: ./scripts/check_output.sh output/correct

DIR=${1:-output/correct}
TMP=$(mktemp -d)
OK=1

# The order of executed transactions, without timestamp and server name
order() {
    grep "Received transaction:" "$1" | sed 's/^.*Received transaction: //'
}

order "$DIR/rep1_a.txt" > "$TMP/ref"

echo "== Order of executed transactions (compared with rep1_a)"
for f in "$DIR"/rep*.txt; do
    order "$f" > "$TMP/this"
    n=$(wc -l < "$TMP/this" | tr -d ' ')
    # A late joiner got the earlier transactions by state transfer, so its
    # list must be the LAST n lines of the reference list
    if tail -n "$n" "$TMP/ref" | diff -q - "$TMP/this" > /dev/null; then
        echo "  OK    $(basename "$f"): $n transactions, same order"
    else
        echo "  DIFF  $(basename "$f")"
        OK=0
    fi
done

echo "== Final balance (last getQuickBalance)"
for f in "$DIR"/rep*.txt; do
    printf "  %-10s %s\n" "$(basename "$f" .txt)" \
        "$(grep "Quick balance" "$f" | tail -1 | sed 's/^.*Quick balance/Quick balance/')"
done
if [ "$(for f in "$DIR"/rep*.txt; do grep "Quick balance" "$f" | tail -1 | sed 's/^.*Quick balance//'; done | sort -u | wc -l)" -ne 1 ]; then
    OK=0
fi

rm -rf "$TMP"

if [ $OK -eq 1 ]; then
    echo "ALL CHECKS PASSED"
else
    echo "SOME CHECKS FAILED"
    exit 1
fi
