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
    d = Diagram("01_system", "1 Hele systemet", 1700, 1220)
    d.title("Hele systemet: en replikert bankkonto over Java RMI",
            "Én Message Delivery server bestemmer rekkefølgen · hver "
            "bankserver er en full kopi (replika) av kontoen · alle "
            "replikaer utfører det samme i samme rekkefølge")

    # ---- legend (left of the server)
    d.box(30, 95, 360, 335,
          "**Pilene er RMI-kall\n"
          "~~(begge sider er RMI-servere, Note 3)\n"
          "\n"
          "**1  broadcastTransactions(liste)\n"
          "~~bank → server, hvert 10. sekund\n"
          "**2  receiveTransaction(id, liste)\n"
          "~~server → alle banker, parallelt\n"
          "**3  ack(navn, id)\n"
          "~~bank → server: «mottatt og utført»\n"
          "**4  join · leave · membershipChanged\n"
          "~~medlemskap begge veier\n"
          "**5  updateState(BankState)\n"
          "~~server → ny bank (late join)",
          "io", fs=14, align="left", valign="top")

    # ---- Message Delivery server
    d.container(420, 95, 860, 335,
                "**Message Delivery server  ·  MessageDeliveryServer.jar\n"
                "~~«Hovedkontoret»: bestemmer rekkefølgen, har ingen saldo selv",
                "mds")
    d.box(440, 175, 190, 110, "**RMI-register\n~~port 1099\n~~bankene finner\n~~serveren her", "white", fs=14)
    d.box(440, 305, 190, 110, "**Medlemsliste\n`bankServers\n~~endres ved join,\n~~leave og FAIL", "white", fs=14)
    d.box(650, 175, 200, 110, "**Meldingskø (FIFO)\n`messageQueue\n~~hver melding får\n~~id 0, 1, 2, …", "white", fs=14)
    d.box(870, 175, 190, 110, "**Leveringsløkke\n`processMessages()\n~~én melding om\n~~gangen", "white", fs=14)
    d.box(1080, 175, 180, 110, "**ACK-sporing\n`pendingAcks\n~~2 s → send på nytt\n~~5 s → FAIL", "white", fs=14)
    d.box(650, 305, 200, 110, "**State transfer\n~~kopi av tilstanden\n~~til ny replika", "white", fs=14)
    d.box(870, 305, 390, 110, "**Medlemsvarsel\n`notifyMembershipChanged()\n~~ny medlemsliste til alle bankene\n~~etter join, leave og FAIL", "white", fs=14)
    d.edge([(850, 230), (870, 230)], color=C_DOWN)
    d.edge([(1060, 230), (1080, 230)], color=C_DOWN)

    # ---- late joiner
    d.box(1330, 95, 340, 180,
          "**Bank D  (kommer senere)\n"
          "~~startes mens gruppa allerede kjører\n"
          "~~får hele tilstanden i én kopi\n"
          "~~og deretter alle nye meldinger",
          "bank", fs=14, dashed=True)
    d.edge([(1280, 140), (1330, 140)], "4", C_GREY, start=True, badge=True, fs=13)
    d.edge([(1280, 360), (1500, 360), (1500, 275)], "5", C_DOWN, badge=True, fs=13, pos=0.4)

    # ---- three replicas
    for i, (x0, name) in enumerate([(30, "A"), (590, "B"), (1150, "C")]):
        d.container(x0, 560, 520, 500,
                    "**Bankserver %s  ·  BankServer.jar\n"
                    "~~«Filial»: full kopi av kontoen" % name, "bank")
        d.box(x0 + 20, 635, 230, 95, "**Kommandotråd\n`BankServerHelper\n~~deposit, addInterest,\n~~getHistory, …", "thread", fs=14)
        d.box(x0 + 270, 635, 230, 95, "**Broadcast-tråd\n~~sender innkurven\n~~hvert 10. sekund", "thread", fs=14)
        d.box(x0 + 20, 750, 480, 65, "**outstanding_collection\n~~«innkurven»: laget her, ikke ordnet ennå", "data", fs=14)
        d.box(x0 + 20, 835, 480, 65, "**receiveTransaction()\n~~utfører transaksjonene i serverens rekkefølge", "whiteg", fs=14)
        d.box(x0 + 20, 920, 150, 120, "**Saldo per\n**valuta\n~~USD, NOK,\n~~EUR, …", "data", fs=14)
        d.box(x0 + 185, 920, 150, 120, "**executed_list\n`order_counter\n~~historikken", "data", fs=14)
        d.box(x0 + 350, 920, 150, 120, "**executedIds\n~~alle utførte\n~~id-er (stopper\n~~duplikater)", "data", fs=14)
        d.edge([(x0 + 135, 730), (x0 + 135, 750)], color=C_PURPLE)
        d.edge([(x0 + 385, 750), (x0 + 385, 730)], color=C_PURPLE)
        for xx in (95, 260, 425):
            d.edge([(x0 + xx, 900), (x0 + xx, 920)], color=C_UP)
        # inputs
        d.box(x0 + 20, 1100, 230, 70, "**Kommandofil / terminal\n~~én kommando per linje", "io", fs=14)
        d.box(x0 + 270, 1100, 230, 70, "**TradingRate.txt\n~~kurs mot USD", "io", fs=14)
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
        ("**Hovedkontoret med nummerautomaten\n~~gir hvert bilag et løpenummer og sender det ut",
         "**Message Delivery server\n~~setter alle meldinger i én global rekkefølge", "mds"),
        ("**En filial med sin egen kopi av hovedboka",
         "**Bankserver (replika)\n~~saldoer, executed_list, order_counter", "bank"),
        ("**Kunden ved skranken",
         "**Kommando fra terminalen eller kommandofila", "io"),
        ("**Innkurven på pulten\n~~fylt ut, men ikke bokført ennå",
         "**outstanding_collection", "data"),
        ("**Kureren til hovedkontoret, hvert 10. minutt",
         "**Broadcast-tråden, hvert 10. sekund\n~~broadcastTransactions(liste)", "thread"),
        ("**Bokføring i hovedboka i løpenummer-rekkefølge",
         "**receiveTransaction()\n~~utfør · order_counter++ · executed_list", "bank"),
        ("**Kvittering tilbake til hovedkontoret",
         "**ack(navn, meldings-id)", "mds"),
        ("**«Bokført»-stempel på hvert bilag",
         "**executedIds\n~~samme transaksjon blir aldri utført to ganger", "data"),
        ("**Rask titt i egen hovedbok",
         "**getQuickBalance\n~~mangler det som ligger i innkurven", "bank"),
        ("**«Si saldoen når alt jeg har levert er bokført»",
         "**getSyncedBalance\n~~markør i køen (korrekt) / vent på tom innkurv (naiv)", "thread"),
        ("**Ny filial åpner og får en kopi av hovedboka",
         "**Late join: updateState(BankState)", "mds"),
        ("**Filial som ikke svarer, stenges",
         "**Ingen ACK: send på nytt etter 2 s, FAIL etter 5 s", "fail"),
    ]
    h = 150 + len(rows) * 88 + 40
    d = Diagram("02_analogi", "2 Bankanalogien", 1500, h)
    d.title("Bankanalogien: én konto, mange filialer, ett hovedkontor",
            "Hver filial har sin egen kopi av hovedboka. De er bare like "
            "fordi alle bokfører de samme bilagene i samme rekkefølge.")
    d.box(60, 100, 600, 40, "**I banken", "note", fs=17)
    d.box(840, 100, 600, 40, "**I systemet vårt", "mds", fs=17)
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
    d = Diagram("03_total_orden", "3 Hvorfor total rekkefølge", 1600, 900)
    d.title("Hvorfor alle må utføre transaksjonene i samme rekkefølge",
            "Begge replikaene har 100 USD. Samtidig gir filial A "
            "«deposit USD 100» og filial B «addInterest USD 10».")

    # left: no common order
    d.container(30, 100, 750, 640, "**Uten felles rekkefølge\n~~hver replika utfører i den rekkefølgen meldingene tilfeldigvis kommer", "fail")
    for x, first, second, mid, end in [
            (70, "deposit USD 100\n~~(fra A)", "addInterest USD 10\n~~(fra B)", "200 USD", "220 USD"),
            (430, "addInterest USD 10\n~~(fra B)", "deposit USD 100\n~~(fra A)", "110 USD", "210 USD")]:
        d.box(x, 190, 280, 40, "**Replika %s" % ("1" if x == 70 else "2"), "plain", fs=15)
        d.box(x + 40, 250, 200, 50, "**100 USD", "data", fs=16)
        d.edge([(x + 140, 300), (x + 140, 390)], first, C_GREY, fs=13)
        d.box(x + 40, 390, 200, 50, "**" + mid, "data", fs=16)
        d.edge([(x + 140, 440), (x + 140, 530)], second, C_GREY, fs=13)
        d.box(x + 40, 530, 200, 50, "**" + end, "data", fs=16)
    d.box(70, 620, 670, 90, "**220 ≠ 210: replikaene er uenige om saldoen\n~~innskudd og rente kommuterer ikke, rekkefølgen endrer svaret", "fail", fs=16)

    # right: with the Message Delivery server
    d.container(820, 100, 750, 640, "**Med Message Delivery server\n~~serveren gir meldingene ett løpenummer, alle utfører i den rekkefølgen", "bank")
    d.box(985, 180, 420, 90, "**Message Delivery server\n`#7  deposit USD 100     (A)\n`#8  addInterest USD 10  (B)", "mds", fs=15)
    for x in (860, 1220):
        d.box(x + 40, 330, 200, 50, "**100 USD", "data", fs=16)
        d.edge([(x + 140, 380), (x + 140, 430)], "#7", C_DOWN, fs=13)
        d.box(x + 40, 430, 200, 50, "**200 USD", "data", fs=16)
        d.edge([(x + 140, 480), (x + 140, 530)], "#8", C_DOWN, fs=13)
        d.box(x + 40, 530, 200, 50, "**220 USD", "data", fs=16)
    d.edge([(1100, 270), (1100, 300), (1000, 300), (1000, 330)], color=C_DOWN)
    d.edge([(1290, 270), (1290, 300), (1360, 300), (1360, 330)], color=C_DOWN)
    d.box(860, 620, 670, 90, "**220 = 220: alle replikaer er like\n~~samme start + samme operasjoner i samme rekkefølge = samme slutt", "bank", fs=16)

    d.box(30, 770, 1540, 100,
          "**Dette er en replikert tilstandsmaskin.\n"
          "~~Kontoen er en tilstandsmaskin: tilstanden er saldoene, og hver transaksjon er en overgang.\n"
          "~~Starter alle replikaer i samme tilstand (0.0) og utfører de samme overgangene i samme rekkefølge, ender de i samme tilstand.\n"
          "~~Den totale rekkefølgen er alt Message Delivery serveren trenger å garantere.",
          "io", fs=15, align="left")
    return d


# =====================================================================
# 4  Life of a transaction (sequence)
# =====================================================================
@page
def lifecycle():
    d = Diagram("04_transaksjon", "4 En transaksjons livsløp", 1600, 1330)
    d.title("En transaksjons livsløp: «deposit NOK 100» på bank A",
            "Fra kommandoen skrives til alle tre replikaene har utført den")
    X = {"user": 150, "A": 450, "mds": 820, "B": 1160, "C": 1450}
    heads = [("user", "**Bruker / kommandofil", "io"), ("A", "**Bankserver A", "bank"),
             ("mds", "**Message Delivery server", "mds"), ("B", "**Bankserver B", "bank"),
             ("C", "**Bankserver C", "bank")]
    for k, t, kind in heads:
        d.box(X[k] - 110, 95, 220, 50, t, kind, fs=15)
        d.edge([(X[k], 145), (X[k], 1110)], color="#aaaaaa", dashed=True, end=False, sw=1.5, under=True)

    d.edge([(X["user"], 190), (X["A"], 190)], "deposit NOK 100", C_GREY, fs=13)
    d.box(470, 215, 330, 115,
          "**1  Lag en Transaction\n`command  = \"deposit NOK 100\"\n`uniqueId = \"A 0\"\n~~legg i outstanding_collection\n~~outstanding_counter: 0 → 1",
          "data", fs=13, align="left")
    d.edge([(X["user"], 370), (X["A"], 370)], "getQuickBalance NOK", C_GREY, fs=13)
    d.edge([(X["A"], 405), (X["user"], 405)], "0.00  (ikke bokført ennå!)", C_RED, dashed=True, fs=13)
    d.box(470, 435, 330, 60, "**2  Venter på broadcast-tråden\n~~opptil 10 sekunder", "thread", fs=13, align="left")

    d.edge([(X["A"], 535), (X["mds"], 535)], "broadcastTransactions([deposit NOK 100 [A 0]])", C_UP, fs=12)
    d.box(840, 560, 300, 90,
          "**3  Får meldings-id 7\n~~legges bakerst i køen\n~~sendes når melding 6 er ACK-et av alle",
          "mds", fs=13, align="left")

    d.edge([(X["mds"], 680), (X["A"], 680)], "receiveTransaction(7, …)", C_DOWN, fs=12)
    d.edge([(X["mds"], 700), (X["B"], 700)], "receiveTransaction(7, …)", C_DOWN, fs=12)
    d.edge([(X["mds"], 725), (X["C"], 725)], "receiveTransaction(7, …)  (parallelt)", C_DOWN, fs=12, pos=0.62)

    for k in ("A", "B", "C"):
        extra = "\n~~fjern [A 0] fra outstanding" if k == "A" else "\n~~(ingenting å fjerne)"
        d.box(X[k] - 140, 760, 280, 125,
              "**4  Utfør\n~~saldo NOK += 100\n~~order_counter++\n~~legg i executed_list + executedIds" + extra,
              "bank", fs=13, align="left")
    d.edge([(X["A"], 925), (X["mds"], 925)], "ack(A, 7)", C_UP, dashed=True, fs=12)
    d.edge([(X["B"], 945), (X["mds"], 945)], "ack(B, 7)", C_UP, dashed=True, fs=12)
    d.edge([(X["C"], 970), (X["mds"], 970)], "ack(C, 7)", C_UP, dashed=True, fs=12, pos=0.38)
    d.box(840, 1000, 300, 95,
          "**5  Alle 3 ACK er kommet\n~~→ melding 8 kan sendes\n~~mangler én: resend etter 2 s,\n~~FAIL etter 5 s",
          "mds", fs=13, align="left")

    d.box(30, 1140, 1540, 130,
          "**Resultat\n"
          "~~Alle tre replikaene har utført «A 0» som samme ordrenummer, og saldoen er lik overalt.\n"
          "~~Mellom 1 og 4 viser getQuickBalance den gamle saldoen. Det er derfor getSyncedBalance finnes.\n"
          "~~A ser sin egen transaksjon komme tilbake på samme måte som de andre, og bokfører den først da.",
          "io", fs=15, align="left")
    return d


# =====================================================================
# 5  Message Delivery server loop: ACK, resend, FAIL
# =====================================================================
@page
def mds_loop():
    d = Diagram("05_ack_fail", "5 ACK, resend og FAIL", 1600, 1060)
    d.title("Message Delivery serveren: én melding om gangen, ACK, resend og FAIL",
            "Løkka i processMessages(), og hva som skjer når en bank ikke svarer")

    d.box(150, 180, 300, 70, "**Ta neste melding fra køen\n~~messageQueue.take(), venter hvis tom", "mds", fs=14)
    d.box(150, 290, 300, 70, "**Øyeblikksbilde av medlemslista\n~~pendingAcks = alle medlemmer", "mds", fs=14)
    d.box(150, 400, 300, 70, "**Send til alle samtidig\n~~receiveTransaction(id, liste)", "mds", fs=14)
    d.box(180, 510, 240, 120, "**Alle ACK\n**innen 2 s?", "white", fs=14, shape="rhombus")
    d.box(500, 535, 260, 70, "**Send på nytt\n~~til de som mangler (samme id)", "note", fs=14)
    d.box(510, 650, 240, 120, "**Alle ACK\n**innen 5 s?", "white", fs=14, shape="rhombus")
    d.box(500, 820, 260, 100, "**FAIL\n~~fjern fra medlemslista\n~~notifyMembershipChanged()", "fail", fs=14)

    d.edge([(300, 250), (300, 290)], color=C_DOWN)
    d.edge([(300, 360), (300, 400)], color=C_DOWN)
    d.edge([(300, 470), (300, 510)], color=C_DOWN)
    d.edge([(180, 570), (100, 570), (100, 200), (150, 200)], "ja", C_UP, fs=13, pos=0.1)
    d.edge([(420, 570), (500, 570)], "nei", C_RED, fs=13)
    d.edge([(630, 605), (630, 650)], color=C_ORANGE)
    d.edge([(510, 710), (70, 710), (70, 215), (150, 215)], "ja", C_UP, fs=13, pos=0.08)
    d.edge([(630, 770), (630, 820)], "nei", C_RED, fs=13)
    d.edge([(500, 870), (40, 870), (40, 230), (150, 230)], "neste melding", C_GREY, fs=13, pos=0.18)
    d.box(130, 950, 630, 80,
          "**leave() (exit) fjerner banken fra pendingAcks med en gang\n"
          "~~serveren venter aldri på en bank som har meldt seg ut",
          "io", fs=14, align="left")

    # timeline
    d.container(820, 120, 750, 470, "**Tidslinje for én melding\n~~A og B svarer, C har krasjet", "plain", fs=15)
    x0, sc = 920, 108
    d.edge([(x0, 520), (x0 + 5 * sc + 40, 520)], color="#333333", sw=1.5)
    for t in range(6):
        d.edge([(x0 + t * sc, 512), (x0 + t * sc, 528)], color="#333333", end=False, sw=1.5)
        d.text(x0 + t * sc - 30, 532, 60, 24, "**%d s" % t, fs=13, align="center")
    for y, name in [(250, "A"), (330, "B"), (410, "C")]:
        d.text(840, y - 14, 70, 28, "**Bank " + name, fs=14)
    d.box(x0 + 20, 232, 150, 36, "**ACK etter 0,1 s", "bank", fs=13)
    d.box(x0 + 40, 312, 150, 36, "**ACK etter 0,3 s", "bank", fs=13)
    d.box(x0 - 15, 392, 30, 36, "", "mds", fs=13)
    d.box(x0 + 2 * sc - 70, 392, 140, 36, "**send på nytt", "note", fs=13)
    d.box(x0 + 5 * sc - 70, 392, 140, 36, "**FAIL, fjernet", "fail", fs=13)
    d.text(x0 - 5, 440, 300, 50, "~~sendt ved 0 s, ingen ACK", fs=13)

    d.box(820, 620, 750, 105,
          "**Hvorfor vente på alle ACK før neste melding?\n"
          "~~Da kan ingen replika ligge mer enn én melding bak,\n"
          "~~og alle får meldingene i nøyaktig samme rekkefølge.",
          "mds", fs=14, align="left")
    d.box(820, 745, 750, 105,
          "**Hvorfor FAIL?\n"
          "~~En krasjet bank ville ellers blokkert hele gruppa for alltid.\n"
          "~~Oppgaven: tolerer at banker forsvinner, og fortsett.",
          "fail", fs=14, align="left")
    d.box(820, 870, 750, 105,
          "**Duplikater er ufarlige\n"
          "~~En resend kan gi samme melding to ganger. Banken ser id-en\n"
          "~~i executedIds, hopper over transaksjonen, men sender ACK likevel.",
          "data", fs=14, align="left")
    return d


# =====================================================================
# 6  Data structures and counters
# =====================================================================
@page
def data_structures():
    d = Diagram("06_datastrukturer", "6 Datastrukturer og tellere", 1600, 880)
    d.title("Datastrukturene i en replika: hvor en transaksjon befinner seg",
            "outstanding_collection → (ordnet) → executed_list, og to tellere som aldri nullstilles")

    d.box(30, 125, 190, 90, "**Kommando\n`deposit NOK 100", "io", fs=14)
    d.box(270, 110, 260, 120, "**Utestående\n~~i outstanding_collection\n~~får id \"A 4\"\n~~outstanding_counter: 4 → 5", "data", fs=14)
    d.box(750, 125, 240, 90, "**Ordnet\n~~har fått plass i den\n~~globale rekkefølgen", "mds", fs=14)
    d.box(1100, 100, 470, 140, "**Utført\n~~saldo endret · order_counter++\n~~lagt i executed_list og executedIds\n~~fjernet fra outstanding_collection (hos A)", "bank", fs=14)
    d.edge([(220, 170), (270, 170)], color=C_GREY)
    d.edge([(530, 170), (750, 170)], "hvert 10. s\n~~(kan sendes flere ganger)", C_UP, fs=12)
    d.edge([(990, 170), (1100, 170)], "første gang", C_DOWN, fs=12)
    d.box(750, 320, 240, 110, "**Hoppes over\n~~id-en finnes i executedIds\n~~ACK sendes likevel", "fail", fs=14)
    d.edge([(870, 215), (870, 320)], "samme id igjen?", C_RED, fs=12)
    d.box(1100, 320, 470, 110, "**Ute av historikken\n~~fjernet fra executed_list av cleanHistory\n~~men fortsatt i executedIds → aldri utført på nytt", "io", fs=14, dashed=True)
    d.edge([(1335, 240), (1335, 320)], "cleanHistory", C_GREY, fs=12)

    d.box(30, 480, 740, 370,
          "**Eksempel: getHistory\n"
          "`executed_list  = [deposit NOK 100, addInterest NOK 10]\n"
          "`outstanding    = [deposit NOK -50, addInterest NOK 5]\n"
          "`order_counter  = 5\n"
          "\n"
          "**Utskrift\n"
          "`executed_list\n"
          "`3. 2026-10-14 10:00:01.123 deposit NOK 100\n"
          "`4. 2026-10-14 10:00:11.456 addInterest NOK 10\n"
          "`outstanding_collection\n"
          "`deposit NOK -50\n"
          "`addInterest NOK 5\n"
          "\n"
          "~~Utførte transaksjoner har nummer og tidspunkt for utførelse.\n"
          "~~Utestående har ingen nummer ennå: de får det først når de er ordnet.",
          "whitey", fs=14, align="left", valign="top")

    d.box(810, 480, 760, 115,
          "**Hvorfor starter nummereringen på 3?\n"
          "~~første nummer = order_counter − executed_list.size() = 5 − 2 = 3\n"
          "~~cleanHistory tømte lista etter transaksjon 0–2, men order_counter\n"
          "~~nullstilles aldri. En transaksjon har samme nummer på alle replikaer.",
          "data", fs=14, align="left")
    d.box(810, 608, 760, 115,
          "**checkTxStatus <id>\n"
          "~~EXECUTED: id-en finnes i executedIds\n"
          "~~OUTSTANDING: ligger i outstanding_collection\n"
          "~~UNKNOWN: ingen av delene   ·   «checkTxStatus 4» = «<denne banken> 4»",
          "data", fs=14, align="left")
    d.box(810, 736, 760, 114,
          "**To tellere som aldri nullstilles\n"
          "~~outstanding_counter: lager id-er, teller bare deposit og addInterest\n"
          "~~order_counter: antall utførte transaksjoner (= neste ordrenummer)\n"
          "~~getSyncedBalance-markører får egne id-er: \"A sync-0\", \"A sync-1\", …",
          "data", fs=14, align="left")
    return d


# =====================================================================
# 7  getSyncedBalance naive vs correct
# =====================================================================
@page
def synced():
    d = Diagram("07_getsyncedbalance", "7 getSyncedBalance", 1600, 1040)
    d.title("getSyncedBalance: korrekt (markør i køen) mot naiv (vent på tom innkurv)",
            "Samme kommandoer på bank A: deposit USD 100 · getSyncedBalance USD · deposit NOK 100  (1 NOK = 0,10 USD)")

    def cmds(y):
        d.box(60, y, 230, 50, "**1  deposit USD 100", "io", fs=14)
        d.box(330, y, 270, 50, "**2  getSyncedBalance USD", "thread", fs=14)
        d.box(640, y, 230, 50, "**3  deposit NOK 100", "io", fs=14)
        d.edge([(290, y + 25), (330, y + 25)], color=C_GREY)
        d.edge([(600, y + 25), (640, y + 25)], color=C_GREY)

    # correct
    d.container(30, 100, 1540, 400, "**Korrekt (standard)\n~~forespørselen er en markør i outstanding_collection og blir ordnet som en vanlig transaksjon", "bank")
    cmds(180)
    d.text(60, 250, 600, 26, "**outstanding_collection ved neste broadcast:", fs=14)
    d.box(60, 280, 250, 55, "**deposit USD 100\n`A 0", "data", fs=13)
    d.box(320, 280, 290, 55, "**getSyncedBalance USD\n`A sync-0   (markør)", "thread", fs=13)
    d.box(620, 280, 250, 55, "**deposit NOK 100\n`A 1", "data", fs=13)
    d.edge([(465, 335), (465, 385)], "samme rekkefølge på alle replikaer", C_DOWN, fs=12)
    d.box(60, 385, 250, 70, "**utfør\n~~saldo: 100 USD", "bank", fs=14)
    d.box(320, 385, 290, 70, "**markør: bare A svarer\n**Synced balance: 100.00", "thread", fs=14, sw=3)
    d.box(620, 385, 250, 70, "**utfør\n~~saldo: 110 USD totalt", "bank", fs=14)
    d.edge([(310, 420), (320, 420)], color=C_GREY)
    d.edge([(610, 420), (620, 420)], color=C_GREY)
    d.box(920, 175, 620, 305,
          "**Svaret er tilstanden akkurat der forespørselen står\n"
          "~~alt som er ordnet før markøren er med, ingenting etter\n"
          "\n"
          "**Alltid ferdig etter én broadcast-runde\n"
          "~~≤ 10 s + levering, uansett hvor mye trafikk det er\n"
          "\n"
          "**Andre replikaer ignorerer markøren\n"
          "~~den fjernes fra outstanding_collection, men legges\n"
          "~~ikke i executed_list og teller ikke i order_counter\n"
          "\n"
          "**Blokkerer ikke kommandoene\n"
          "~~svaret skrives ut når markøren kommer tilbake",
          "whiteg", fs=14, align="left", valign="top")

    # naive
    d.container(30, 525, 1540, 380, "**Naiv  (-Dsync=naive)\n~~svar først når outstanding_collection er tom", "fail")
    cmds(600)
    d.text(60, 668, 600, 26, "**outstanding_collection ved neste broadcast:", fs=14)
    d.box(60, 698, 250, 55, "**deposit USD 100\n`A 0", "data", fs=13)
    d.box(320, 698, 250, 55, "**deposit NOK 100\n`A 1", "data", fs=13)
    d.box(580, 698, 290, 55, "**egen tråd sjekker hvert 200 ms:\n**«er innkurven tom?»", "thread", fs=13)
    d.box(60, 785, 810, 90, "**Innkurven er tom først når begge er utført\n**Synced balance: 110.00\n~~tar med deposit NOK 100, som ble gitt ETTER forespørselen", "fail", fs=14, sw=3)
    d.box(920, 600, 620, 275,
          "**Venter på et øyeblikk med tom innkurv\n"
          "~~svaret tar med transaksjoner som kom etter forespørselen\n"
          "\n"
          "**Kan vente for alltid\n"
          "~~kommer det stadig nye transaksjoner, blir innkurven\n"
          "~~aldri tom (starvation, «deadlock» i oppgaveteksten)\n"
          "\n"
          "**Observert med eksempelfila\n"
          "~~2. kall: 100.00 (korrekt) mot 110.00 (naiv)\n"
          "~~4. kall: 120.00 (korrekt) mot 306.00 (naiv)",
          "plain", fs=14, align="left", valign="top", stroke=C_RED)
    d.box(30, 930, 1540, 80,
          "**Begge versjonene blokkerer ikke kommandoløkka.\n"
          "~~Bare slik kan nye transaksjoner komme inn mens forespørselen venter, og forskjellen blir synlig.",
          "io", fs=14, align="left")
    return d


# =====================================================================
# 8  Membership
# =====================================================================
@page
def membership():
    d = Diagram("08_medlemskap", "8 Medlemskap", 1700, 1080)
    d.title("Medlemskap: oppstart, ny replika, utmelding og krasj",
            "Serveren eier medlemslista. Alle endringer sendes til alle med membershipChanged()")
    panels = [
        ("A  Oppstart", "bank", [
            ("**Bank A: join\n~~medlemmer 1/3 → venter", "bank"),
            ("**Bank B: join\n~~medlemmer 2/3 → venter", "bank"),
            ("**Bank C: join\n~~medlemmer 3/3", "bank"),
            ("**membershipChanged til alle\n~~alle ser 3 medlemmer", "mds"),
            ("**Alle starter\n~~broadcast-tråd + kommandoer", "thread"),
        ], "**Hvorfor vente?\n~~Ellers kan A sende en transaksjon\n~~før C er med, og C mangler den\n~~for alltid (C får ingen kopi)."),
        ("B  Ny replika (late join)", "mds", [
            ("**Bank D: join", "bank"),
            ("**Vent til meldingen som pågår\n**er ACK-et av alle\n~~ingen melding er halvveis levert", "mds"),
            ("**Hent tilstanden fra en replika\n~~BankState: saldoer, executed_list,\n~~order_counter, executedIds", "data"),
            ("**updateState(state) til D\n~~D er nå lik de andre", "mds"),
            ("**D legges i medlemslista\n~~membershipChanged til alle", "mds"),
            ("**D får alle meldinger fra nå", "bank"),
        ], "**D ser allerede ≥ N medlemmer\n~~og starter med en gang.\n~~Tilstanden og meldingene etterpå\n~~henger sammen uten hull."),
        ("C  Utmelding (exit)", "thread", [
            ("**exit, eller slutten av fila", "io"),
            ("**Send resten av innkurven\n~~de andre utfører dem likevel", "thread"),
            ("**Vent på ubesvarte\n**getSyncedBalance\n~~maks 30 s", "thread"),
            ("**leave(): fjernes straks\n~~serveren venter ikke på ACK", "mds"),
            ("**membershipChanged\n**til de andre", "mds"),
            ("**System.exit(0)\n~~RMI-tråder holder ellers\n~~JVM-en i live", "io"),
        ], "**Leave er planlagt\n~~Ingen grunn til å vente 5 s\n~~på en bank som har sagt\n~~fra at den går."),
        ("D  Krasj", "fail", [
            ("**Prosessen dør\n~~kill, Ctrl+C, maskin nede", "fail"),
            ("**Ingen ACK på meldingen", "fail"),
            ("**Etter 2 s: send på nytt", "note"),
            ("**Etter 5 s: FAIL\n~~fjernes fra medlemslista", "fail"),
            ("**membershipChanged\n**til de gjenværende", "mds"),
            ("**Resten fortsetter\n~~med lik tilstand", "bank"),
        ], "**Krasj oppdages, leave meldes\n~~En krasjet prosess kan ikke si\n~~fra selv, så serveren må merke\n~~det på at ACK-en uteblir."),
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
    d = Diagram("09_traader", "9 Tråder og låser", 1600, 1000)
    d.title("Trådene i en bankserver, og hvorfor de må låse",
            "Fire–fem tråder bruker den samme tilstanden samtidig")
    d.container(560, 300, 480, 330, "**Delt tilstand i BankServer\n~~beskyttet av synchronized (låsen på objektet)", "data")
    d.box(590, 390, 420, 215,
          "`balances\n`outstandingCollection\n`executedList\n`executedIds\n`orderCounter, outstandingCounter\n`pendingSyncResults",
          "whitey", fs=15, align="left")
    d.box(40, 140, 450, 150,
          "**Kommandotråd (main)\n~~leser terminalen eller fila (0,5–1,5 s mellom linjene)\n~~deposit, addInterest: legger i outstanding\n~~getHistory, checkTxStatus: leser",
          "thread", fs=14, align="left")
    d.box(40, 500, 450, 130,
          "**Broadcast-tråd\n~~hvert 10. s: kopi av outstanding_collection\n~~→ broadcastTransactions()",
          "thread", fs=14, align="left")
    d.box(1110, 140, 450, 150,
          "**RMI-tråd: receiveTransaction()\n~~kalt av serveren for hver melding\n~~utfører, svarer egne markører, sender ACK",
          "thread", fs=14, align="left")
    d.box(1110, 500, 450, 150,
          "**RMI-tråder: membershipChanged(),\n**updateState()\n~~membershipChanged bruker en egen lås\n~~(membershipLock) og vekker oppstarten",
          "thread", fs=14, align="left")
    d.box(560, 720, 480, 100,
          "**Naiv getSyncedBalance-tråd\n~~bare med -Dsync=naive\n~~sjekker hvert 200 ms om innkurven er tom",
          "thread", fs=14, dashed=True)
    d.edge([(490, 215), (560, 360)], "synchronized", C_PURPLE, fs=12)
    d.edge([(490, 565), (560, 565)], "synchronized", C_PURPLE, fs=12)
    d.edge([(1110, 215), (1040, 360)], "synchronized", C_PURPLE, fs=12)
    d.edge([(1110, 575), (1040, 575)], "updateState", C_PURPLE, fs=12)
    d.edge([(800, 720), (800, 650)], "synchronized", C_PURPLE, fs=12)
    d.box(40, 860, 750, 120,
          "**Hvorfor lås?\n"
          "~~Uten lås kan receiveTransaction fjerne en transaksjon fra innkurven\n"
          "~~mens broadcast-tråden kopierer den: feil resultat eller\n"
          "~~ConcurrentModificationException.",
          "io", fs=14, align="left")
    d.box(810, 860, 750, 120,
          "**Hvorfor vente uten lås?\n"
          "~~getSyncedBalance venter på svaret uten å holde låsen.\n"
          "~~Ellers kunne receiveTransaction aldri levert svaret: vranglås.",
          "io", fs=14, align="left")
    return d


# =====================================================================
# 10  Running the test scenario
# =====================================================================
@page
def running():
    d = Diagram("10_kjoring", "10 Kjøring og testscenario", 1600, 900)
    d.title("Slik kjøres testscenariet: seks terminaler",
            "Eksempel: 3 replikaer fra start, én ny etter 5 s og én ny etter 15 s (sjekk mot årets README.txt)")
    x0, sc = 560, 10.5
    d.edge([(x0, 640), (x0 + 95 * sc, 640)], color="#333333", sw=1.5)
    for t in (0, 5, 15, 30, 45, 60, 75, 90):
        d.edge([(x0 + t * sc, 632), (x0 + t * sc, 648)], color="#333333", end=False, sw=1.5)
        d.text(x0 + t * sc - 30, 652, 60, 24, "**%d s" % t, fs=13, align="center")
    rows = [
        ("**Terminal 1: Message Delivery server\n`java -jar jars/MessageDeliveryServer.jar 1099", 0, 95, "mds", "kjører hele tiden"),
        ("**Terminal 2: replika (Rep1)\n`… group01 3 TradingRate.txt Rep1.txt", 0, 82, "bank", "Rep1"),
        ("**Terminal 3: replika (Rep1)", 0, 81, "bank", "Rep1"),
        ("**Terminal 4: replika (Rep1)", 0, 83, "bank", "Rep1"),
        ("**Terminal 5: ny replika (Rep2)", 5, 80, "bank", "Rep2 · late join"),
        ("**Terminal 6: ny replika (Rep3)", 15, 86, "bank", "Rep3 · late join"),
    ]
    y = 110
    for label, t0, t1, kind, bar in rows:
        d.box(30, y, 510, 70, label, "io", fs=13, align="left")
        d.box(int(x0 + t0 * sc), y + 15, int((t1 - t0) * sc), 40, "**" + bar, kind, fs=13,
              dashed=(t0 > 0))
        y += 85
    d.box(30, 700, 1540, 170,
          "**Kommandoen for en replika\n"
          "`java -jar jars/BankServer.jar localhost:1099 group01 3 input/final/TradingRate.txt input/final/Rep1.txt 2>&1 | tee output/final/rep1_a.txt\n"
          "\n"
          "~~Naiv versjon: legg til -Dsync=naive før -jar.   Andre maskiner: legg til -Djava.rmi.server.hostname=<egen IP>.\n"
          "~~De tre første venter på hverandre (3/3). Replikaene som starter ved 5 s og 15 s ser allerede ≥ 3 medlemmer, får tilstanden og starter med en gang.\n"
          "~~Etterpå: alle replikaer som levde til slutten skal ha samme sluttsaldo, og samme transaksjon på hvert ordrenummer.",
          "io", fs=14, align="left")
    return d


def build():
    return [fn() for fn in ALL]
