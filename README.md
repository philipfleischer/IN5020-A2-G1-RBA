# IN5020 Assignment 2: Replicated Bank Account (Group 1)

Replicated bank account using a totally ordered broadcast, implemented with Java RMI.
The Message Delivery server orders the transactions and sends them to all bank servers, and waits for an ACK from every replica before sending the next message.
All replicas execute the same transactions in the same order.

Requires Java 11 or newer.

## Build
(macOS/Linux): ./scripts/build.sh

(Windows): scripts\build.bat

This creates jars/MessageDeliveryServer.jar and jars/BankServer.jar.

## Run

java -jar jars/MessageDeliveryServer.jar [port]

java [-Dsync=correct|naive] -jar jars/BankServer.jar <server address> <account name> <number of replicas> <currency file> [command file]

- server address is host:port, e.g. localhost:1099 (default port 1099).
- Each replica waits until <number of replicas> have joined before it starts.
- With a command file, one command is read every 0.5-1.5 s and the server exits at the end.
- -Dsync chooses the getSyncedBalance version (default correct).
- On several machines, add -Djava.rmi.server.hostname=<own IP> to every process.

Official scenario (3 replicas with Rep1 at 0 s, 2 with Rep2 at 7 s, 1 with Rep3 at 12 s):

(logs in output/correct/): ./scripts/run_final.sh correct

(logs in output/naive/): ./scripts/run_final.sh naive

## Assumptions

- No currency in getQuickBalance/getSyncedBalance means USD.
- checkTxStatus 0 means transaction 0 created by this replica. The full id also works.
- Only deposit and addInterest use outstanding_counter. getSyncedBalance requests get their own ids.
- Every output line starts with a timestamp.

## getSyncedBalance

Naive: waits until outstanding_collection is empty and then prints the balance. If new transactions keep coming it can wait a long time (or forever), and the answer can include transactions issued after the request.
In output/naive/rep3_a.txt, getSyncedBalance at 15:36:26 got its answer 2 s later, and the answer included "deposit EUR -150", which was issued after the request.

Correct: puts a marker in outstanding_collection that is ordered like a normal transaction. When it comes back, the replica that issued it prints the balance. The marker is not added to executed_list. The answer is the state at the request's place in the total order, and it comes after at most one broadcast round plus delivery.

Both versions run without blocking the command loop.

## Design

- Exactly once: executed ids are kept in a set that cleanHistory does not clear, so duplicates (re-broadcasts and resends) are skipped.
- exit: sends remaining transactions, calls leave() and exits.
- Crash: the server sends to all replicas in parallel, resends after 2 s and marks a replica as FAIL after 5 s. Calls the server makes while holding a lock have a timeout, so a frozen replica cannot block it.
- Late join: join() waits until the current message is ACKed, gets the state from an existing replica (getState) and gives it to the new one (updateState).

## Results

- Official scenario, correct: same order on all 6 replicas, all end with 5566.82 USD.
- Official scenario, naive: same order on all 6 replicas, all end with 5608.24 USD.
- Failure demo: resend after 2 s, FAIL after 5 s, A and B both end with 100.00 USD.

The balance differs between runs because it depends on timing.

## Workload

- Håkon Gulliksrud (haakgull@uio.no): RMI setup, Message Delivery server (join, queue, ACK), first version of the bank server.
- Anwar Ahmed Hersi (anwarahe@uio.no): getSyncedBalance, currency conversion, GetSyncedBalanceDemo, resend/FAIL timeout, late join.
- Philip Elias Fleischer (philipef@uio.no): commands, exactly-once, naive/correct switch, logging, frozen-replica fix.

## Use of AI

We used an AI tool (Claude) during this assignment, in these ways:

- **Debugging:** to help find and understand bugs in our code.
- **General help:** questions about Java, RMI, threads and how to approach parts of the assignment.
- **README:** the first draft of this README was made with AI, based on our code and test output.
- **Scripts:** the first versions of the build and run scripts in `scripts/` were made with AI.
- **Timestamps (`Log.java`):** the assignment asks for a timestamp on every output line, which also includes lines we do not print ourselves, such as stack traces and RMI errors. We used AI to suggest a solution for this, which led to replacing System.out and System.err with a stream that adds the timestamp. We then tested and changed it ourselves.

We have read, tested and changed everything ourselves, fixed what did not work, and made our own choices about the design. Everyone in the group understands the solution, and we take full responsibility for everything we hand in.
