# IN5020 Assignment 2: Replicated Bank Account (Group 1)

A replicated bank account built as a **replicated state machine** on top of a
**totally ordered broadcast**, implemented with **Java RMI**.

- **Message Delivery server** (`MessageDeliveryServer.jar`): receives lists of
  transactions from the bank servers, puts them in one global order, and sends
  them to every bank server. It waits for an ACK from every bank server before it
  sends the next message.
- **Bank server** (`BankServer.jar`): one replica of the account. All replicas
  execute the same transactions in the same order, so they all end with the same
  balances.

Requires **Java 11 or newer**. No external libraries.

---

## 1. Build

The jar files are built from `src/` with:

```bash
./scripts/build.sh          # macOS / Linux
scripts\build.bat           # Windows
```

This creates `jars/MessageDeliveryServer.jar` and `jars/BankServer.jar`.

---

## 2. Run

Run all commands from the project folder.

### 2.1 Message Delivery server

```bash
java -jar jars/MessageDeliveryServer.jar [port]
```

`port` is optional (default `1099`). The server creates the RMI registry on this
port.

### 2.2 Bank server

```bash
java [-Dsync=correct|naive] -jar jars/BankServer.jar <server address> <account name> <number of replicas> <currency file> [command file]
```

| Argument | Meaning |
|---|---|
| `<server address>` | `host:port` of the Message Delivery server, e.g. `localhost:1099` |
| `<account name>` | Name of the group/account, e.g. `group01`. The same for all replicas |
| `<number of replicas>` | Number of replicas started at the beginning. Each replica waits until this many have joined |
| `<currency file>` | Trading rates, one `<Currency> <rate to USD>` per line (USD is always 1) |
| `[command file]` | Optional. Without it, commands are read from the terminal. With it, one command is read every 0.5–1.5 s, and the server exits at the end |
| `-Dsync=correct\|naive` | Optional. Which `getSyncedBalance` implementation to use (default `correct`, see section 4) |

### 2.3 Example: three replicas on one machine

```bash
# Terminal 1
java -jar jars/MessageDeliveryServer.jar 1099

# Terminals 2, 3 and 4
java -jar jars/BankServer.jar localhost:1099 group01 3 input/example/TradingRate.txt input/example/ExampleInputFile.txt
```

The first two replicas print `Waiting for replicas: x/3` until the third one has
joined. Then all three run the command file and exit.

### 2.4 Several machines

RMI calls go in both directions (the Message Delivery server calls the bank
servers back), so every process must tell RMI which address the others can reach
it on:

```bash
java -Djava.rmi.server.hostname=<this machine's IP> -jar jars/MessageDeliveryServer.jar 1099
java -Djava.rmi.server.hostname=<this machine's IP> -jar jars/BankServer.jar <server IP>:1099 group01 3 ...
```

---

## 3. Commands

| Command | What it does |
|---|---|
| `deposit <Currency> <amount>` | Creates a transaction, adds it to `outstanding_collection`. Applied on all replicas when it is ordered |
| `addInterest [<Currency>] <percent>` | Like `deposit`. Multiplies the balance of `<Currency>` (or of every currency, if none is given) by `1 + percent/100` |
| `getQuickBalance [<Currency>]` | Prints the local balance right away, converted to `<Currency>` |
| `getSyncedBalance [<Currency>]` | Prints the balance after all transactions this replica issued before the command have been applied (section 4) |
| `getHistory` | Prints `executed_list` (order number, execution time, command) and `outstanding_collection` |
| `checkTxStatus <id>` | Prints `EXECUTED`, `OUTSTANDING` or `UNKNOWN` for a transaction |
| `cleanHistory` | Empties `executed_list`. `order_counter` and `outstanding_counter` are not reset |
| `memberInfo` | Prints the names of the current members of the group |
| `sleep <seconds>` | Does nothing for the given time |
| `exit` | Sends the remaining outstanding transactions, leaves the group and exits |

**Our assumptions:**

- A missing `<Currency>` in `getQuickBalance` / `getSyncedBalance` means **USD**,
  the base currency of the trading-rate file.
- `checkTxStatus 0` (just a number) means transaction number 0 created by **this**
  replica. The full id also works: `checkTxStatus BankServer-49478-a3f2 0`.
- Only `deposit` and `addInterest` are numbered with `outstanding_counter`, as in the
  assignment text. `getSyncedBalance` requests get their own ids (`... sync-0`).
- Every output line starts with the time it was printed. `getHistory` also shows when
  each transaction was **executed** on this replica.

---

## 4. The two `getSyncedBalance` implementations

Both versions run **without blocking the command loop**: the command is registered,
the next commands go on, and the answer is printed when it is ready. Only the way of
waiting is different. This is what makes the difference visible.

| | **Naive** (`-Dsync=naive`) | **Correct** (default) |
|---|---|---|
| Idea | Wait for a moment when `outstanding_collection` is empty, then print the balance | Put a `getSyncedBalance` marker into `outstanding_collection`. It is broadcast and ordered like a deposit |
| Who answers | The replica that issued the command | Only the replica that issued the command. Other replicas ignore the marker. It is removed from `outstanding_collection` and **not** added to `executed_list` |
| When | Only when the collection is empty. If new transactions keep coming, this can be **never** (starvation, the "deadlock" in the assignment) | When the marker comes back ordered: after at most one 10 s broadcast round plus delivery time |
| What the answer includes | Everything ordered before the collection happened to become empty, **including transactions issued after the request** | Exactly the state at the request's position in the total order: all earlier transactions, nothing later |

### What we observed

Same input (`input/example/ExampleInputFile.txt`, one replica), only the mode is
different. Logs in `output/example/correct/` and `output/example/naive/`:

| `getSyncedBalance` | Correct | Naive | Why the naive answer is different |
|---|---|---|---|
| 1 (before any deposit) | 0.00 USD | 0.00 USD | Nothing outstanding |
| 2 (after `deposit USD 100`) | **100.00 USD** | **110.00 USD** | Includes `deposit NOK 100`, issued *after* the request |
| 3 (after `sleep 10`) | 110.00 USD | 110.00 USD | Everything applied |
| 4 (after `addInterest USD 10`) | **120.00 USD** | **306.00 USD** | Includes six deposits issued after the request |
| 5 | **3177.00 NOK** | **6516.40 NOK** | Includes everything up to the end of the file |
| 6 (last) | 651.64 USD | 651.64 USD | No new commands, so both agree |

The naive answers depend on timing and change from run to run. The correct answers
are always the same.


---

## 5. Design in short

- **Total order.** Bank servers send `outstanding_collection` to the Message Delivery
  server every 10 s. The server puts every message in one queue and delivers them one
  at a time to all replicas, waiting for all ACKs before the next one. So every
  replica sees the same messages in the same order.
- **Exactly once.** The same transaction can arrive twice (it is re-broadcast every
  10 s until it is ordered, and the server resends after a missing ACK). Every replica
  keeps the ids of all executed transactions in a set that `cleanHistory` never
  clears, and skips duplicates. Because all replicas see the same order, they all skip
  the same duplicates.
- **Start-up.** A replica waits until `<number of replicas>` members have joined
  before it starts broadcasting or reading commands, so no initial replica misses a
  message.
- **Leave vs. crash.** `exit` first sends the remaining outstanding transactions, then
  calls `leave()` on the server, which removes the replica at once and sends the new
  member list to the others. A crashed replica is detected by the ACK timeout:
  resend after 2 s, marked as FAIL and removed after 5 s.
  `TODO: check when the timeout code is merged.`
- **Late join.** `TODO: describe the state transfer (balances, executed_list,
  order_counter, executed ids) when it is merged.`
- **Threads.** Each bank server has a command thread, a broadcast thread and the RMI
  threads that deliver messages. Shared state is protected by `synchronized`.

---

## 6. Folder structure

| Path | Content |
|---|---|
| `src/common/` | `Transaction`, `Message`, `Timestamp`, `Log` (timestamp on every output line) |
| `src/message_delivery_server/` | `MessageDeliveryServer` and its RMI interface |
| `src/bank_server/` | `BankServer` (the replica), `BankServerHelper` (command parsing), `CurrencyConverter`, `BankState` (state transfer), `GetSyncedBalanceDemo` |
| `scripts/` | Build scripts |
| `input/example/` | The example input file (transaction ids filled in) and trading rates |
| `input/final/` | `TODO: the official input files` |
| `output/example/` | Logs from our test runs: one replica correct / naive, three replicas |
| `output/final/` | `TODO: logs from all replicas for the official input files` |

---

## 7. Workload distribution

| Member | Work |
|---|---|
| Håkon Gulliksrud: haakgull@uio.no | RMI set-up, Message Delivery server (join, ordering queue, ACK handling), first version of the bank server. `TODO: timeout / late join, if Håkon did it` |
| Anwar Ahmed Hersi: anwarahe@uio.no | `getSyncedBalance` (naive and correct), currency conversion, `GetSyncedBalanceDemo` |
| Philip Elias Fleischer: philipef@uio.no | Commands (`checkTxStatus`, `getHistory`, `memberInfo`, `exit`/leave, waiting for all replicas), exactly-once execution, naive/correct switch, logging with timestamps, build scripts, testing, README |
