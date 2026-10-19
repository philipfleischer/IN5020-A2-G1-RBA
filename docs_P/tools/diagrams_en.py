"""
English version of the diagrams in diagrams.py. Same geometry, translated
text. Used for the README and the portfolio page.

    python3 render_svg_en.py ../diagrammer/en
"""
from dsl import Diagram, C_UP, C_DOWN, C_GREY, C_RED, C_ORANGE, C_PURPLE

ALL = []


def page(fn):
    ALL.append(fn)
    return fn


# =====================================================================
# 1  Whole system
# =====================================================================
@page
def system():
    d = Diagram("01_system", "1 The whole system", 1700, 1220)
    d.title("The whole system: a replicated bank account over Java RMI",
            "One Message Delivery server decides the order · every bank "
            "server is a full copy (replica) of the account · every "
            "replica runs the same transactions in the same order")

    # ---- legend (left of the server)
    d.box(30, 95, 360, 335,
          "**The arrows are RMI calls\n"
          "~~(both sides are RMI servers, Note 3)\n"
          "\n"
          "**1  broadcastTransactions(list)\n"
          "~~bank → server, every 10 seconds\n"
          "**2  receiveTransaction(id, list)\n"
          "~~server → all banks, in parallel\n"
          "**3  ack(name, id)\n"
          "~~bank → server: \"received and executed\"\n"
          "**4  join · leave · membershipChanged\n"
          "~~membership, both directions\n"
          "**5  updateState(BankState)\n"
          "~~server → new bank (late join)",
          "io", fs=14, align="left", valign="top")

    # ---- Message Delivery server
    d.container(420, 95, 860, 335,
                "**Message Delivery server  ·  MessageDeliveryServer.jar\n"
                "~~\"Head office\": decides the order, holds no balance itself",
                "mds")
    d.box(440, 175, 190, 110, "**RMI registry\n~~port 1099\n~~where the banks\n~~find the server", "white", fs=14)
    d.box(440, 305, 190, 110, "**Member list\n`bankServers\n~~changes on join,\n~~leave and FAIL", "white", fs=14)
    d.box(650, 175, 200, 110, "**Message queue (FIFO)\n`messageQueue\n~~each message gets\n~~id 0, 1, 2, …", "white", fs=14)
    d.box(870, 175, 190, 110, "**Delivery loop\n`processMessages()\n~~one message\n~~at a time", "white", fs=14)
    d.box(1080, 175, 180, 110, "**ACK tracking\n`pendingAcks\n~~2 s → resend\n~~5 s → FAIL", "white", fs=14)
    d.box(650, 305, 200, 110, "**State transfer\n~~copy of the state\n~~to a new replica", "white", fs=14)
    d.box(870, 305, 390, 110, "**Membership notice\n`notifyMembershipChanged()\n~~new member list to every bank\n~~after join, leave and FAIL", "white", fs=14)
    d.edge([(850, 230), (870, 230)], color=C_DOWN)
    d.edge([(1060, 230), (1080, 230)], color=C_DOWN)

    # ---- late joiner
    d.box(1330, 95, 340, 180,
          "**Bank D  (joins later)\n"
          "~~started while the group is running\n"
          "~~gets the whole state in one copy\n"
          "~~and then every new message",
          "bank", fs=14, dashed=True)
    d.edge([(1280, 140), (1330, 140)], "4", C_GREY, start=True, badge=True, fs=13)
    d.edge([(1280, 360), (1500, 360), (1500, 275)], "5", C_DOWN, badge=True, fs=13, pos=0.4)

    # ---- three replicas
    for i, (x0, name) in enumerate([(30, "A"), (590, "B"), (1150, "C")]):
        d.container(x0, 560, 520, 500,
                    "**Bank server %s  ·  BankServer.jar\n"
                    "~~\"Branch\": a full copy of the account" % name, "bank")
        d.box(x0 + 20, 635, 230, 95, "**Command thread\n`BankServerHelper\n~~deposit, addInterest,\n~~getHistory, …", "thread", fs=14)
        d.box(x0 + 270, 635, 230, 95, "**Broadcast thread\n~~sends the inbox\n~~every 10 seconds", "thread", fs=14)
        d.box(x0 + 20, 750, 480, 65, "**outstanding_collection\n~~\"the inbox\": created here, not ordered yet", "data", fs=14)
        d.box(x0 + 20, 835, 480, 65, "**receiveTransaction()\n~~executes transactions in the server's order", "whiteg", fs=14)
        d.box(x0 + 20, 920, 150, 120, "**Balance per\n**currency\n~~USD, NOK,\n~~EUR, …", "data", fs=14)
        d.box(x0 + 185, 920, 150, 120, "**executed_list\n`order_counter\n~~the history", "data", fs=14)
        d.box(x0 + 350, 920, 150, 120, "**executedIds\n~~every executed\n~~id (stops\n~~duplicates)", "data", fs=14)
        d.edge([(x0 + 135, 730), (x0 + 135, 750)], color=C_PURPLE)
        d.edge([(x0 + 385, 750), (x0 + 385, 730)], color=C_PURPLE)
        for xx in (95, 260, 425):
            d.edge([(x0 + xx, 900), (x0 + xx, 920)], color=C_UP)
        # inputs
        d.box(x0 + 20, 1100, 230, 70, "**Command file / terminal\n~~one command per line", "io", fs=14)
        d.box(x0 + 270, 1100, 230, 70, "**TradingRate.txt\n~~rates against USD", "io", fs=14)
        d.edge([(x0 + 135, 1100), (x0 + 135, 1060)], color=C_GREY)
        d.edge([(x0 + 385, 1100), (x0 + 385, 1060)], color=C_GREY)

        # RMI arrows between this bank and the server
        tops = {"A": [x0 + 300, x0 + 350, x0 + 400, x0 + 450],
                "B": [x0 + 130, x0 + 200, x0 + 270, x0 + 340],
                "C": [x0 + 70, x0 + 120, x0 + 170, x0 + 220]}[name]
        mds = {"A": [460, 510, 560, 610],
               "B": [x0 + 130, x0 + 200, x0 + 270, x0 + 340],
               "C": [1090, 1140, 1190, 1240]}[name]
        d.edge([(tops[0], 560), (mds[0], 430)], "1", C_UP, badge=True, fs=13)
        d.edge([(mds[1], 430), (tops[1], 560)], "2", C_DOWN, badge=True, fs=13)
        d.edge([(tops[2], 560), (mds[2], 430)], "3", C_UP, dashed=True, badge=True, fs=13)
        d.edge([(tops[3], 560), (mds[3], 430)], "4", C_GREY, start=True, badge=True, fs=13)
    return d


# =====================================================================
# 2  Bank analogy
# =====================================================================
@page
def analogy():
    rows = [
        ("**Head office with the ticket machine\n~~gives every slip a running number and sends it out",
         "**Message Delivery server\n~~puts every message in one global order", "mds"),
        ("**A branch with its own copy of the ledger",
         "**Bank server (replica)\n~~balances, executed_list, order_counter", "bank"),
        ("**The customer at the counter",
         "**A command from the terminal or the command file", "io"),
        ("**The inbox on the desk\n~~filled in, but not booked yet",
         "**outstanding_collection", "data"),
        ("**The courier to head office, every 10 minutes",
         "**The broadcast thread, every 10 seconds\n~~broadcastTransactions(list)", "thread"),
        ("**Booking in the ledger in running-number order",
         "**receiveTransaction()\n~~execute · order_counter++ · executed_list", "bank"),
        ("**A receipt back to head office",
         "**ack(name, message id)", "mds"),
        ("**A \"booked\" stamp on every slip",
         "**executedIds\n~~the same transaction is never executed twice", "data"),
        ("**A quick look in your own ledger",
         "**getQuickBalance\n~~misses whatever is still in the inbox", "bank"),
        ("**\"Tell me the balance once everything I handed in is booked\"",
         "**getSyncedBalance\n~~marker in the queue (correct) / wait for empty inbox (naive)", "thread"),
        ("**A new branch opens and gets a copy of the ledger",
         "**Late join: updateState(BankState)", "mds"),
        ("**A branch that stops answering is closed",
         "**No ACK: resend after 2 s, FAIL after 5 s", "fail"),
    ]
    h = 150 + len(rows) * 88 + 40
    d = Diagram("02_analogi", "2 The bank analogy", 1500, h)
    d.title("The bank analogy: one account, many branches, one head office",
            "Every branch has its own copy of the ledger. They only stay "
            "equal because they all book the same slips in the same order.")
    d.box(60, 100, 600, 40, "**In the bank", "note", fs=17)
    d.box(840, 100, 600, 40, "**In our system", "mds", fs=17)
    y = 160
    for left, right, kind in rows:
        d.box(60, y, 600, 72, left, "note", fs=15)
        d.box(840, y, 600, 72, right, kind, fs=15)
        d.edge([(660, y + 36), (840, y + 36)], color=C_GREY, start=True)
        y += 88
    return d


# =====================================================================
# 3  Why total order
# =====================================================================
@page
def total_order():
    d = Diagram("03_total_orden", "3 Why total order", 1600, 900)
    d.title("Why every replica must execute transactions in the same order",
            "Both replicas hold 100 USD. At the same time branch A issues "
            "\"deposit USD 100\" and branch B issues \"addInterest USD 10\".")

    # left: no common order
    d.container(30, 100, 750, 640, "**Without a common order\n~~each replica executes messages in whatever order they happen to arrive", "fail")
    for x, first, second, mid, end in [
            (70, "deposit USD 100\n~~(from A)", "addInterest USD 10\n~~(from B)", "200 USD", "220 USD"),
            (430, "addInterest USD 10\n~~(from B)", "deposit USD 100\n~~(from A)", "110 USD", "210 USD")]:
        d.box(x, 190, 280, 40, "**Replica %s" % ("1" if x == 70 else "2"), "plain", fs=15)
        d.box(x + 40, 250, 200, 50, "**100 USD", "data", fs=16)
        d.edge([(x + 140, 300), (x + 140, 390)], first, C_GREY, fs=13)
        d.box(x + 40, 390, 200, 50, "**" + mid, "data", fs=16)
        d.edge([(x + 140, 440), (x + 140, 530)], second, C_GREY, fs=13)
        d.box(x + 40, 530, 200, 50, "**" + end, "data", fs=16)
    d.box(70, 620, 670, 90, "**220 ≠ 210: the replicas disagree on the balance\n~~deposit and interest do not commute, the order changes the result", "fail", fs=16)

    # right: with the Message Delivery server
    d.container(820, 100, 750, 640, "**With a Message Delivery server\n~~the server gives each message a sequence number, everyone follows it", "bank")
    d.box(985, 180, 420, 90, "**Message Delivery server\n`#7  deposit USD 100     (A)\n`#8  addInterest USD 10  (B)", "mds", fs=15)
    for x in (860, 1220):
        d.box(x + 40, 330, 200, 50, "**100 USD", "data", fs=16)
        d.edge([(x + 140, 380), (x + 140, 430)], "#7", C_DOWN, fs=13)
        d.box(x + 40, 430, 200, 50, "**200 USD", "data", fs=16)
        d.edge([(x + 140, 480), (x + 140, 530)], "#8", C_DOWN, fs=13)
        d.box(x + 40, 530, 200, 50, "**220 USD", "data", fs=16)
    d.edge([(1100, 270), (1100, 300), (1000, 300), (1000, 330)], color=C_DOWN)
    d.edge([(1290, 270), (1290, 300), (1360, 300), (1360, 330)], color=C_DOWN)
    d.box(860, 620, 670, 90, "**220 = 220: every replica is equal\n~~same start + same operations in the same order = same end state", "bank", fs=16)

    d.box(30, 770, 1540, 100,
          "**This is a replicated state machine.\n"
          "~~The account is a state machine: the state is the balances, and every transaction is a transition.\n"
          "~~If every replica starts in the same state (0.0) and applies the same transitions in the same order, they end in the same state.\n"
          "~~The total order is the only thing the Message Delivery server has to guarantee.",
          "io", fs=15, align="left")
    return d


# =====================================================================
# 4  Life of a transaction (sequence)
# =====================================================================
@page
def lifecycle():
    d = Diagram("04_transaksjon", "4 Life of a transaction", 1600, 1330)
    d.title("Life of a transaction: \"deposit NOK 100\" on bank A",
            "From the moment the command is typed until all three replicas have executed it")
    X = {"user": 150, "A": 450, "mds": 820, "B": 1160, "C": 1450}
    heads = [("user", "**User / command file", "io"), ("A", "**Bank server A", "bank"),
             ("mds", "**Message Delivery server", "mds"), ("B", "**Bank server B", "bank"),
             ("C", "**Bank server C", "bank")]
    for k, t, kind in heads:
        d.box(X[k] - 110, 95, 220, 50, t, kind, fs=15)
        d.edge([(X[k], 145), (X[k], 1110)], color="#aaaaaa", dashed=True, end=False, sw=1.5, under=True)

    d.edge([(X["user"], 190), (X["A"], 190)], "deposit NOK 100", C_GREY, fs=13)
    d.box(470, 215, 330, 115,
          "**1  Create a Transaction\n`command  = \"deposit NOK 100\"\n`uniqueId = \"A 0\"\n~~add to outstanding_collection\n~~outstanding_counter: 0 → 1",
          "data", fs=13, align="left")
    d.edge([(X["user"], 370), (X["A"], 370)], "getQuickBalance NOK", C_GREY, fs=13)
    d.edge([(X["A"], 405), (X["user"], 405)], "0.00  (not booked yet!)", C_RED, dashed=True, fs=13)
    d.box(470, 435, 330, 60, "**2  Waits for the broadcast thread\n~~up to 10 seconds", "thread", fs=13, align="left")

    d.edge([(X["A"], 535), (X["mds"], 535)], "broadcastTransactions([deposit NOK 100 [A 0]])", C_UP, fs=12)
    d.box(840, 560, 300, 90,
          "**3  Gets message id 7\n~~added to the back of the queue\n~~sent once everyone has ACKed 6",
          "mds", fs=13, align="left")

    d.edge([(X["mds"], 680), (X["A"], 680)], "receiveTransaction(7, …)", C_DOWN, fs=12)
    d.edge([(X["mds"], 700), (X["B"], 700)], "receiveTransaction(7, …)", C_DOWN, fs=12)
    d.edge([(X["mds"], 725), (X["C"], 725)], "receiveTransaction(7, …)  (in parallel)", C_DOWN, fs=12, pos=0.62)

    for k in ("A", "B", "C"):
        extra = "\n~~remove [A 0] from outstanding" if k == "A" else "\n~~(nothing to remove)"
        d.box(X[k] - 140, 760, 280, 125,
              "**4  Execute\n~~balance NOK += 100\n~~order_counter++\n~~add to executed_list + executedIds" + extra,
              "bank", fs=13, align="left")
    d.edge([(X["A"], 925), (X["mds"], 925)], "ack(A, 7)", C_UP, dashed=True, fs=12)
    d.edge([(X["B"], 945), (X["mds"], 945)], "ack(B, 7)", C_UP, dashed=True, fs=12)
    d.edge([(X["C"], 970), (X["mds"], 970)], "ack(C, 7)", C_UP, dashed=True, fs=12, pos=0.38)
    d.box(840, 1000, 300, 95,
          "**5  All 3 ACKs are in\n~~→ message 8 can be sent\n~~one missing: resend after 2 s,\n~~FAIL after 5 s",
          "mds", fs=13, align="left")

    d.box(30, 1140, 1540, 130,
          "**Result\n"
          "~~All three replicas have executed \"A 0\" with the same order number, and the balance is the same everywhere.\n"
          "~~Between steps 1 and 4, getQuickBalance shows the old balance. That is why getSyncedBalance exists.\n"
          "~~A sees its own transaction come back just like the others do, and only books it then.",
          "io", fs=15, align="left")
    return d


# =====================================================================
# 5  Message Delivery server loop: ACK, resend, FAIL
# =====================================================================
@page
def mds_loop():
    d = Diagram("05_ack_fail", "5 ACK, resend and FAIL", 1600, 1060)
    d.title("The Message Delivery server: one message at a time, ACK, resend and FAIL",
            "The loop in processMessages(), and what happens when a bank stops answering")

    d.box(150, 180, 300, 70, "**Take the next message\n~~messageQueue.take(), waits if empty", "mds", fs=14)
    d.box(150, 290, 300, 70, "**Snapshot of the member list\n~~pendingAcks = every member", "mds", fs=14)
    d.box(150, 400, 300, 70, "**Send to everyone at once\n~~receiveTransaction(id, list)", "mds", fs=14)
    d.box(180, 510, 240, 120, "**All ACKs\n**within 2 s?", "white", fs=14, shape="rhombus")
    d.box(500, 535, 260, 70, "**Resend\n~~to the missing ones (same id)", "note", fs=14)
    d.box(510, 650, 240, 120, "**All ACKs\n**within 5 s?", "white", fs=14, shape="rhombus")
    d.box(500, 820, 260, 100, "**FAIL\n~~remove from member list\n~~notifyMembershipChanged()", "fail", fs=14)

    d.edge([(300, 250), (300, 290)], color=C_DOWN)
    d.edge([(300, 360), (300, 400)], color=C_DOWN)
    d.edge([(300, 470), (300, 510)], color=C_DOWN)
    d.edge([(180, 570), (100, 570), (100, 200), (150, 200)], "yes", C_UP, fs=13, pos=0.1)
    d.edge([(420, 570), (500, 570)], "no", C_RED, fs=13)
    d.edge([(630, 605), (630, 650)], color=C_ORANGE)
    d.edge([(510, 710), (70, 710), (70, 215), (150, 215)], "yes", C_UP, fs=13, pos=0.08)
    d.edge([(630, 770), (630, 820)], "no", C_RED, fs=13)
    d.edge([(500, 870), (40, 870), (40, 230), (150, 230)], "next message", C_GREY, fs=13, pos=0.18)
    d.box(130, 950, 630, 80,
          "**leave() (exit) removes the bank from pendingAcks right away\n"
          "~~the server never waits for a bank that has signed off",
          "io", fs=14, align="left")

    # timeline
    d.container(820, 120, 750, 470, "**Timeline for one message\n~~A and B answer, C has crashed", "plain", fs=15)
    x0, sc = 920, 108
    d.edge([(x0, 520), (x0 + 5 * sc + 40, 520)], color="#333333", sw=1.5)
    for t in range(6):
        d.edge([(x0 + t * sc, 512), (x0 + t * sc, 528)], color="#333333", end=False, sw=1.5)
        d.text(x0 + t * sc - 30, 532, 60, 24, "**%d s" % t, fs=13, align="center")
    for y, name in [(250, "A"), (330, "B"), (410, "C")]:
        d.text(840, y - 14, 70, 28, "**Bank " + name, fs=14)
    d.box(x0 + 20, 232, 150, 36, "**ACK after 0.1 s", "bank", fs=13)
    d.box(x0 + 40, 312, 150, 36, "**ACK after 0.3 s", "bank", fs=13)
    d.box(x0 - 15, 392, 30, 36, "", "mds", fs=13)
    d.box(x0 + 2 * sc - 70, 392, 140, 36, "**resend", "note", fs=13)
    d.box(x0 + 5 * sc - 70, 392, 140, 36, "**FAIL, removed", "fail", fs=13)
    d.text(x0 - 5, 440, 300, 50, "~~sent at 0 s, no ACK", fs=13)

    d.box(820, 620, 750, 105,
          "**Why wait for every ACK before the next message?\n"
          "~~Then no replica can be more than one message behind,\n"
          "~~and everyone gets the messages in exactly the same order.",
          "mds", fs=14, align="left")
    d.box(820, 745, 750, 105,
          "**Why FAIL?\n"
          "~~Otherwise a crashed bank would block the whole group forever.\n"
          "~~The assignment: tolerate banks disappearing, and keep going.",
          "fail", fs=14, align="left")
    d.box(820, 870, 750, 105,
          "**Duplicates are harmless\n"
          "~~A resend can deliver the same message twice. The bank finds the id\n"
          "~~in executedIds, skips the transaction, but still sends the ACK.",
          "data", fs=14, align="left")
    return d


# =====================================================================
# 6  Data structures and counters
# =====================================================================
@page
def data_structures():
    d = Diagram("06_datastrukturer", "6 Data structures and counters", 1600, 880)
    d.title("The data structures in a replica: where a transaction is right now",
            "outstanding_collection → (ordered) → executed_list, and two counters that are never reset")

    d.box(30, 125, 190, 90, "**Command\n`deposit NOK 100", "io", fs=14)
    d.box(270, 110, 260, 120, "**Outstanding\n~~in outstanding_collection\n~~gets id \"A 4\"\n~~outstanding_counter: 4 → 5", "data", fs=14)
    d.box(750, 125, 240, 90, "**Ordered\n~~has a place in the\n~~global order", "mds", fs=14)
    d.box(1100, 100, 470, 140, "**Executed\n~~balance changed · order_counter++\n~~added to executed_list and executedIds\n~~removed from outstanding_collection (on A)", "bank", fs=14)
    d.edge([(220, 170), (270, 170)], color=C_GREY)
    d.edge([(530, 170), (750, 170)], "every 10 s\n~~(may be sent more than once)", C_UP, fs=12)
    d.edge([(990, 170), (1100, 170)], "first time", C_DOWN, fs=12)
    d.box(750, 320, 240, 110, "**Skipped\n~~the id is in executedIds\n~~ACK is still sent", "fail", fs=14)
    d.edge([(870, 215), (870, 320)], "same id again?", C_RED, fs=12)
    d.box(1100, 320, 470, 110, "**Out of the history\n~~removed from executed_list by cleanHistory\n~~but still in executedIds → never executed again", "io", fs=14, dashed=True)
    d.edge([(1335, 240), (1335, 320)], "cleanHistory", C_GREY, fs=12)

    d.box(30, 480, 740, 370,
          "**Example: getHistory\n"
          "`executed_list  = [deposit NOK 100, addInterest NOK 10]\n"
          "`outstanding    = [deposit NOK -50, addInterest NOK 5]\n"
          "`order_counter  = 5\n"
          "\n"
          "**Output\n"
          "`executed_list\n"
          "`3. 2026-10-14 10:00:01.123 deposit NOK 100\n"
          "`4. 2026-10-14 10:00:11.456 addInterest NOK 10\n"
          "`outstanding_collection\n"
          "`deposit NOK -50\n"
          "`addInterest NOK 5\n"
          "\n"
          "~~Executed transactions have a number and the time they were executed.\n"
          "~~Outstanding ones have no number yet: they only get one once they are ordered.",
          "whitey", fs=14, align="left", valign="top")

    d.box(810, 480, 760, 115,
          "**Why does the numbering start at 3?\n"
          "~~first number = order_counter − executed_list.size() = 5 − 2 = 3\n"
          "~~cleanHistory emptied the list after transactions 0–2, but order_counter\n"
          "~~is never reset. A transaction has the same number on every replica.",
          "data", fs=14, align="left")
    d.box(810, 608, 760, 115,
          "**checkTxStatus <id>\n"
          "~~EXECUTED: the id is in executedIds\n"
          "~~OUTSTANDING: it is in outstanding_collection\n"
          "~~UNKNOWN: neither   ·   \"checkTxStatus 4\" = \"<this bank> 4\"",
          "data", fs=14, align="left")
    d.box(810, 736, 760, 114,
          "**Two counters that are never reset\n"
          "~~outstanding_counter: makes ids, counts only deposit and addInterest\n"
          "~~order_counter: number of executed transactions (= next order number)\n"
          "~~getSyncedBalance markers get their own ids: \"A sync-0\", \"A sync-1\", …",
          "data", fs=14, align="left")
    return d


# =====================================================================
# 7  getSyncedBalance naive vs correct
# =====================================================================
@page
def synced():
    d = Diagram("07_getsyncedbalance", "7 getSyncedBalance", 1600, 1040)
    d.title("getSyncedBalance: correct (marker in the queue) vs naive (wait for an empty inbox)",
            "Same commands on bank A: deposit USD 100 · getSyncedBalance USD · deposit NOK 100  (1 NOK = 0.10 USD)")

    def cmds(y):
        d.box(60, y, 230, 50, "**1  deposit USD 100", "io", fs=14)
        d.box(330, y, 270, 50, "**2  getSyncedBalance USD", "thread", fs=14)
        d.box(640, y, 230, 50, "**3  deposit NOK 100", "io", fs=14)
        d.edge([(290, y + 25), (330, y + 25)], color=C_GREY)
        d.edge([(600, y + 25), (640, y + 25)], color=C_GREY)

    # correct
    d.container(30, 100, 1540, 400, "**Correct (default)\n~~the request is a marker in outstanding_collection and is ordered like a normal transaction", "bank")
    cmds(180)
    d.text(60, 250, 600, 26, "**outstanding_collection at the next broadcast:", fs=14)
    d.box(60, 280, 250, 55, "**deposit USD 100\n`A 0", "data", fs=13)
    d.box(320, 280, 290, 55, "**getSyncedBalance USD\n`A sync-0   (marker)", "thread", fs=13)
    d.box(620, 280, 250, 55, "**deposit NOK 100\n`A 1", "data", fs=13)
    d.edge([(465, 335), (465, 385)], "same order on every replica", C_DOWN, fs=12)
    d.box(60, 385, 250, 70, "**execute\n~~balance: 100 USD", "bank", fs=14)
    d.box(320, 385, 290, 70, "**marker: only A answers\n**Synced balance: 100.00", "thread", fs=14, sw=3)
    d.box(620, 385, 250, 70, "**execute\n~~balance: 110 USD in total", "bank", fs=14)
    d.edge([(310, 420), (320, 420)], color=C_GREY)
    d.edge([(610, 420), (620, 420)], color=C_GREY)
    d.box(920, 175, 620, 305,
          "**The answer is the state exactly where the request sits\n"
          "~~everything ordered before the marker is included, nothing after\n"
          "\n"
          "**Always done after one broadcast round\n"
          "~~≤ 10 s + delivery, no matter how much traffic there is\n"
          "\n"
          "**Other replicas ignore the marker\n"
          "~~it is removed from outstanding_collection, but is not\n"
          "~~added to executed_list and does not count in order_counter\n"
          "\n"
          "**Does not block the commands\n"
          "~~the answer is printed when the marker comes back",
          "whiteg", fs=14, align="left", valign="top")

    # naive
    d.container(30, 525, 1540, 380, "**Naive  (-Dsync=naive)\n~~answer only once outstanding_collection is empty", "fail")
    cmds(600)
    d.text(60, 668, 600, 26, "**outstanding_collection at the next broadcast:", fs=14)
    d.box(60, 698, 250, 55, "**deposit USD 100\n`A 0", "data", fs=13)
    d.box(320, 698, 250, 55, "**deposit NOK 100\n`A 1", "data", fs=13)
    d.box(580, 698, 290, 55, "**own thread checks every 200 ms:\n**\"is the inbox empty?\"", "thread", fs=13)
    d.box(60, 785, 810, 90, "**The inbox is only empty once both are executed\n**Synced balance: 110.00\n~~includes deposit NOK 100, which was issued AFTER the request", "fail", fs=14, sw=3)
    d.box(920, 600, 620, 275,
          "**Waits for a moment with an empty inbox\n"
          "~~the answer includes transactions issued after the request\n"
          "\n"
          "**Can wait forever\n"
          "~~if new transactions keep coming, the inbox is never\n"
          "~~empty (starvation, \"deadlock\" in the assignment text)\n"
          "\n"
          "**Observed with the example file\n"
          "~~2nd call: 100.00 (correct) vs 110.00 (naive)\n"
          "~~4th call: 120.00 (correct) vs 306.00 (naive)",
          "plain", fs=14, align="left", valign="top", stroke=C_RED)
    d.box(30, 930, 1540, 80,
          "**Neither version blocks the command loop.\n"
          "~~Only then can new transactions arrive while the request is waiting, and the difference becomes visible.",
          "io", fs=14, align="left")
    return d


# =====================================================================
# 8  Membership
# =====================================================================
@page
def membership():
    d = Diagram("08_medlemskap", "8 Membership", 1700, 1080)
    d.title("Membership: startup, new replica, leaving and crashing",
            "The server owns the member list. Every change is sent to everyone with membershipChanged()")
    panels = [
        ("A  Startup", "bank", [
            ("**Bank A: join\n~~members 1/3 → waits", "bank"),
            ("**Bank B: join\n~~members 2/3 → waits", "bank"),
            ("**Bank C: join\n~~members 3/3", "bank"),
            ("**membershipChanged to all\n~~everyone sees 3 members", "mds"),
            ("**Everyone starts\n~~broadcast thread + commands", "thread"),
        ], "**Why wait?\n~~Otherwise A could send a transaction\n~~before C has joined, and C would\n~~miss it forever (C gets no copy)."),
        ("B  New replica (late join)", "mds", [
            ("**Bank D: join", "bank"),
            ("**Wait until the current message\n**is ACKed by everyone\n~~no message is half delivered", "mds"),
            ("**Fetch the state from a replica\n~~BankState: balances, executed_list,\n~~order_counter, executedIds", "data"),
            ("**updateState(state) to D\n~~D is now equal to the others", "mds"),
            ("**D is added to the member list\n~~membershipChanged to all", "mds"),
            ("**D gets every message from now on", "bank"),
        ], "**D already sees ≥ N members\n~~and starts right away.\n~~The state and the messages after\n~~it fit together with no gap."),
        ("C  Leaving (exit)", "thread", [
            ("**exit, or end of the file", "io"),
            ("**Send the rest of the inbox\n~~the others still execute them", "thread"),
            ("**Wait for unanswered\n**getSyncedBalance\n~~at most 30 s", "thread"),
            ("**leave(): removed at once\n~~the server does not wait for ACK", "mds"),
            ("**membershipChanged\n**to the others", "mds"),
            ("**System.exit(0)\n~~RMI threads would otherwise\n~~keep the JVM alive", "io"),
        ], "**Leaving is planned\n~~No reason to wait 5 s for\n~~a bank that has said it\n~~is going."),
        ("D  Crash", "fail", [
            ("**The process dies\n~~kill, Ctrl+C, machine down", "fail"),
            ("**No ACK for the message", "fail"),
            ("**After 2 s: resend", "note"),
            ("**After 5 s: FAIL\n~~removed from the member list", "fail"),
            ("**membershipChanged\n**to the rest", "mds"),
            ("**The rest carry on\n~~with equal state", "bank"),
        ], "**Crashes are detected, leaves\n**are announced\n~~A crashed process cannot say so\n~~itself, so the server notices\n~~the missing ACK."),
    ]
    for i, (title, kind, steps, note) in enumerate(panels):
        x = 30 + i * 420
        d.container(x, 100, 400, 950, "**" + title, kind, fs=17)
        y = 160
        for j, (t, k) in enumerate(steps):
            d.box(x + 25, y, 350, 95, t, k, fs=14)
            if j < len(steps) - 1:
                d.edge([(x + 200, y + 95), (x + 200, y + 120)], color=C_GREY)
            y += 120
        d.box(x + 25, 900, 350, 130, note, "io", fs=14, align="left")
    return d


# =====================================================================
# 9  Threads and locks
# =====================================================================
@page
def threads():
    d = Diagram("09_traader", "9 Threads and locks", 1600, 1000)
    d.title("The threads in a bank server, and why they need a lock",
            "Four or five threads use the same state at the same time")
    d.container(560, 300, 480, 330, "**Shared state in BankServer\n~~protected by synchronized (the object's lock)", "data")
    d.box(590, 390, 420, 215,
          "`balances\n`outstandingCollection\n`executedList\n`executedIds\n`orderCounter, outstandingCounter\n`pendingSyncResults",
          "whitey", fs=15, align="left")
    d.box(40, 140, 450, 150,
          "**Command thread (main)\n~~reads the terminal or the file (0.5–1.5 s per line)\n~~deposit, addInterest: add to outstanding\n~~getHistory, checkTxStatus: read",
          "thread", fs=14, align="left")
    d.box(40, 500, 450, 130,
          "**Broadcast thread\n~~every 10 s: copy of outstanding_collection\n~~→ broadcastTransactions()",
          "thread", fs=14, align="left")
    d.box(1110, 140, 450, 150,
          "**RMI thread: receiveTransaction()\n~~called by the server for every message\n~~executes, answers own markers, sends ACK",
          "thread", fs=14, align="left")
    d.box(1110, 500, 450, 150,
          "**RMI threads: membershipChanged(),\n**updateState()\n~~membershipChanged uses its own lock\n~~(membershipLock) and wakes up startup",
          "thread", fs=14, align="left")
    d.box(560, 720, 480, 100,
          "**Naive getSyncedBalance thread\n~~only with -Dsync=naive\n~~checks every 200 ms if the inbox is empty",
          "thread", fs=14, dashed=True)
    d.edge([(490, 215), (560, 360)], "synchronized", C_PURPLE, fs=12)
    d.edge([(490, 565), (560, 565)], "synchronized", C_PURPLE, fs=12)
    d.edge([(1110, 215), (1040, 360)], "synchronized", C_PURPLE, fs=12)
    d.edge([(1110, 575), (1040, 575)], "updateState", C_PURPLE, fs=12)
    d.edge([(800, 720), (800, 650)], "synchronized", C_PURPLE, fs=12)
    d.box(40, 860, 750, 120,
          "**Why a lock?\n"
          "~~Without it, receiveTransaction could remove a transaction from the\n"
          "~~inbox while the broadcast thread is copying it: a wrong result or a\n"
          "~~ConcurrentModificationException.",
          "io", fs=14, align="left")
    d.box(810, 860, 750, 120,
          "**Why wait without the lock?\n"
          "~~getSyncedBalance waits for its answer without holding the lock.\n"
          "~~Otherwise receiveTransaction could never deliver the answer: deadlock.",
          "io", fs=14, align="left")
    return d


# =====================================================================
# 10  Running the official test scenario
# =====================================================================
@page
def running():
    d = Diagram("10_kjoring", "10 Running the test scenario", 1600, 960)
    d.title("Running the official test scenario: one server and six replicas",
            "3 replicas with Rep1 at 0 s, 2 new with Rep2 at 7 s, 1 new with Rep3 at 12 s  (scripts/run_final.sh)")
    x0, sc = 560, 10.0
    rows = [
        ("**Server: Message Delivery server\n`java -jar jars/MessageDeliveryServer.jar 1099", 0, 100, "mds", "runs the whole time"),
        ("**Replica 1 (Rep1)\n`… group01 3 TradingRate.txt Rep1.txt", 0, 91, "bank", "Rep1"),
        ("**Replica 2 (Rep1)", 0, 89, "bank", "Rep1"),
        ("**Replica 3 (Rep1)", 0, 90, "bank", "Rep1"),
        ("**Replica 4: new (Rep2)", 7, 94, "bank", "Rep2 · late join"),
        ("**Replica 5: new (Rep2)", 7, 96, "bank", "Rep2 · late join"),
        ("**Replica 6: new (Rep3)", 12, 88, "bank", "Rep3 · late join"),
    ]
    y = 100
    for label, t0, t1, kind, bar in rows:
        d.box(30, y, 510, 70, label, "io", fs=13, align="left")
        d.box(int(x0 + t0 * sc), y + 15, int((t1 - t0) * sc), 40, "**" + bar, kind, fs=13,
              dashed=(t0 > 0))
        y += 80
    ax = y + 20
    d.edge([(x0, ax), (x0 + 100 * sc, ax)], color="#333333", sw=1.5)
    for t in (0, 7, 12, 30, 45, 60, 75, 90):
        d.edge([(x0 + t * sc, ax - 8), (x0 + t * sc, ax + 8)], color="#333333", end=False, sw=1.5)
        d.text(x0 + t * sc - 30, ax + 12, 60, 24, "**%d s" % t, fs=13, align="center")
    d.box(30, ax + 60, 1540, 170,
          "**The command for one replica\n"
          "`java -Dsync=correct -jar jars/BankServer.jar localhost:1099 group01 3 input/TradingRate.txt input/Rep1.txt > output/correct/rep1_a.txt\n"
          "\n"
          "~~Naive version: -Dsync=naive.   Other machines: add -Djava.rmi.server.hostname=<own IP>.\n"
          "~~The first three wait for each other (3/3). The replicas started at 7 s and 12 s already see ≥ 3 members, get the state and start right away.\n"
          "~~Afterwards: every replica that lived to the end must have the same final balance and the same transaction at every order number.",
          "io", fs=14, align="left")
    return d


def build():
    return [fn() for fn in ALL]
