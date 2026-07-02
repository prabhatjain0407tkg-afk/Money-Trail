package com.expensetracker.sms.categorizer

import com.expensetracker.sms.model.Category

/**
 * Keyword-to-category seed rules.
 *
 * Rules are tried in order — the FIRST matching rule wins.
 * Each rule is a pair of (keywords list, category).
 * Matching is done case-insensitively against the normalized merchant name.
 *
 * To extend: add entries to [MERCHANT_RULES] or [BODY_RULES].
 * User overrides are handled by [Categorizer] at runtime — they take priority
 * over everything here without modifying this file.
 */
object CategoryRules {

    /**
     * ATM / cash withdrawal keywords — checked against the FULL SMS body.
     * Matches any debit SMS that involves physical cash: ATM, branch, micro-ATM, cash advance.
     * Checked before investment/utility so a cash-advance SMS isn't misclassified.
     */
    val CASH_PRIORITY_KEYWORDS: List<String> = listOf(

        // ── ATM-specific phrases ─────────────────────────────────────────────
        "atm withdrawal",
        "atm cash withdrawal",
        "atm cash",
        "atm wdl",           // e.g. "ATM WDL INR 5000"
        "atm wd",            // e.g. "ATM WD 5000"
        "atm w/d",
        "atm debit",
        "atm txn",
        "atm transaction",
        "withdrawn at atm",
        "cash withdrawn at atm",
        "cash withdrawal at atm",
        "atm cash debit",
        "atm cash dispensed",
        "atm cash dispense",

        // ── Generic cash withdrawal ──────────────────────────────────────────
        "cash withdrawal",
        "cash withdrawn",
        "cash dispensed",
        "cash disbursed",
        "cash debit",
        "withdraw cash",
        "cash paid out",
        "cash out",

        // ── Micro-ATM / Business Correspondent / AePS ───────────────────────
        "micro atm",
        "microatm",
        "mini atm",
        "aeps withdrawal",   // Aadhaar-enabled Payment System
        "aeps cash",
        "business correspondent",
        "bc withdrawal",

        // ── Branch cash withdrawal ───────────────────────────────────────────
        "branch cash",
        "cash at branch",
        "cash withdrawal at branch",
        "teller withdrawal",
        "over the counter",
        "otc cash",

        // ── POS cash / Cashback at merchant ─────────────────────────────────
        "pos cash",
        "cash at pos",
        "pos cash withdrawal",
        "cash back at pos",  // NOT the cashback reward — POS cash

        // ── Credit card cash advance ─────────────────────────────────────────
        "cash advance",
        "credit card cash",
        "card cash advance",
        "atm cash advance",

        // ── Bank-specific ATM identifiers ────────────────────────────────────
        "atm/pos",           // some banks combine ATM+POS in one channel label
        "cash wdl",          // compact form used in many bank SMS
        "cash w/d",
        "cdm withdrawal",    // Cash Deposit Machine (CDM) withdrawal mode in some banks
        "instant cash"
    )

    /**
     * Credit card bill payment keywords — checked against the FULL SMS body FIRST.
     * Any match routes the transaction to TRANSFER so it is excluded from expense totals.
     * Covers: UPI VPA identifiers, multi-word bill payment phrases, CRED, NEFT/IMPS to CC.
     */
    val CC_PAYMENT_PRIORITY_KEYWORDS: List<String> = listOf(

        // ── Multi-word phrases — unambiguous CC bill payment context ──────────
        "credit card bill payment",
        "credit card bill paid",
        "credit card payment received",
        "credit card payment successful",
        "credit card payment processed",
        "credit card outstanding",
        "credit card dues",
        "credit card emi",
        "cc bill payment",
        "cc bill paid",
        "cc outstanding",
        "card bill payment",
        "card bill paid",
        "paid to credit card",
        "payment to credit card",
        "transfer to credit card",
        "credit card minimum due",
        "minimum amount due",
        "total amount due",
        "bill payment to hdfc credit",
        "bill payment to icici credit",
        "bill payment to axis credit",
        "bill payment to kotak credit",
        "bill payment to sbi card",

        // ── UPI VPAs used exclusively for CC bill payments ────────────────────
        "hdfccrcard",        // HDFC Credit Card  (hdfccrcard@hdfcbank)
        "icicibank.cc",      // ICICI Credit Card (icicibank.cc@icici)
        "icicibkcrcard",     // ICICI CC — alternate SMS text
        "icici bk crcard",   // ICICI CC — bank SMS description
        "axisbankcc",        // Axis Bank Credit Card (axisbankcc@axis)
        "axiscard",          // Axis Card payment VPA
        "kotakbankcc",       // Kotak Bank Credit Card (kotakbankcc@kotak)
        "yesbank.cc",        // Yes Bank Credit Card
        "indusind.cc",       // IndusInd Credit Card
        "rblcard",           // RBL Credit Card
        "idfcfirstcc",       // IDFC FIRST Credit Card
        "amexindia",         // American Express (amexindia@aexp)
        "hsbcnet",           // HSBC Credit Card
        "standardchartered.cc", // Standard Chartered CC
        "sbicrd",            // SBI Card (NEFT/UPI description)
        "aucc@",             // AU Small Finance Bank CC
        "kotakcc",           // Kotak CC alternate VPA

        // ── CRED app payments — always a CC bill payment ──────────────────────
        "via cred",
        "paid via cred",
        "cred payment",
        "cred.club",
        "dreamplug",         // CRED's legal entity name in NEFT descriptions

        // ── Generic NEFT/IMPS/UPI descriptions used by banks for CC payment ───
        "cc payment",
        "crcard payment",
        "creditcard payment",
        "credit card repayment"
    )

    /**
     * High-priority loan EMI keywords checked against the FULL SMS body.
     * Checked AFTER INVESTMENT (so SIP/MF NACH mandates are already resolved) and
     * BEFORE UTILITY — any remaining NACH mandate or loan-instalment phrase → BILLS.
     * Only applied to non-CREDIT transactions (loan disbursements arrive as credits).
     */
    val EMI_PRIORITY_KEYWORDS: List<String> = listOf(
        // EMI / instalment signal phrases
        "loan emi", "emi debit", "emi debited", "emi paid",
        "loan installment", "loan instalment", "loan repayment",
        "towards emi", "for emi", "your emi", "monthly emi",
        "emi of rs", "emi of inr", "emi amount",

        // NACH mandate catch-all — investment NACH already filtered above
        "nach mandate", "nach debit", "nach_debit",
        "nach-emi", "nach-hl", "nach-pl", "nach-cl", "nach-ll",

        // Loan type context in SMS body
        "home loan", "personal loan", "car loan", "auto loan",
        "vehicle loan", "gold loan", "education loan", "business loan",
        "loan account", "loan a/c",

        // NBFC / fintech lenders whose name appears in bank SMS description
        "bajaj finserv", "bajaj finance",
        "capital first", "fullerton india",
        "muthoot finance", "muthoot fincorp",
        "mahindra finance", "shriram finance",
        "tata capital", "aditya birla finance",
        "piramal finance", "navi finserv",

        // Credit card EMI conversion (purchase on EMI — distinct from CC bill payment)
        "emi conversion", "converted to emi",
    )

    /**
     * High-priority investment keywords checked against the FULL SMS body.
     * Checked before utility and merchant rules — any SMS with a transaction amount
     * that contains one of these is always INVESTMENT (NACH-MUT SIP, MF purchase,
     * FD/RD booking, PPF/NPS contribution, demat trades, IPO, digital gold, etc.).
     */
    val INVESTMENT_PRIORITY_KEYWORDS: List<String> = listOf(
        // ── NACH mandates (the user's primary request) ───────────────────────
        "nach-mut", "nach-mf", "nach debit mut", "nach mutual",

        // ── SIP signals ──────────────────────────────────────────────────────
        "sip debit", "sip amount", "sip installment", "sip payment",
        "monthly sip", "sip mandate", "systematic investment",

        // ── Mutual fund body signals ──────────────────────────────────────────
        "mutual fund", "folio number", "unit allotment", "units allotted",
        "nav of", "mf unit", "mf purchase", "mf redemption",
        "nippon mutual", "mirae asset", "axis mutual", "sbi mutual",
        "hdfc mutual", "icici prudential mutual", "kotak mutual",
        "dsp mutual", "franklin mutual", "uti mutual", "parag parikh",

        // ── Fixed / Recurring deposits ────────────────────────────────────────
        "fixed deposit created", "fd created", "fd booked", "fd opened",
        "fixed deposit booked", "fixed deposit opened",
        "rd installment", "recurring deposit installment", "rd debit",

        // ── PPF / NPS ─────────────────────────────────────────────────────────
        "ppf contribution", "ppf deposit", "public provident fund",
        "nps contribution", "national pension system", "nps tier",

        // ── EPFO / Provident Fund ─────────────────────────────────────────────
        "epfo", "employees provident fund", "employee provident fund",
        "epf contribution", "pf contribution", "provident fund contribution",
        "sukanya samriddhi",

        // ── Stock market & demat ─────────────────────────────────────────────
        "demat account", "share purchase", "equity purchase",
        "stock purchase", "ipo application", "asba block", "asba debit",
        "limit order", "buy order executed", "sell order executed",

        // ── Tax-saving & bonds ────────────────────────────────────────────────
        "elss", "tax saving fund",
        "sovereign gold bond", "sgb allotment", "digital gold purchase",
        "nsc certificate", "national savings certificate"
    )

    /**
     * High-priority utility keywords checked against the FULL SMS body before any
     * merchant matching. If any of these appear in the raw text the transaction is
     * always categorised as BILLS — regardless of payment method (CC / debit / UPI).
     */
    val UTILITY_PRIORITY_KEYWORDS: List<String> = listOf(
        // ── Electricity boards & signals ────────────────────────────────────
        "electricity bill", "electric bill", "power bill", "energy bill",
        "units consumed", "bijli bill", "electricity board",
        "bescom", "msedcl", "bses", "tata power", "adani electricity",
        "tneb", "wbsedcl", "jdvvnl", "uppcl", "pspcl", "kseb", "cesc",
        "tpwodl", "tpnodl", "pgvcl", "dgvcl", "ugvcl", "torrent power",
        "jharkhand bijli", "apepdcl", "tsspdcl", "hescom", "gescom",

        // ── Gas bills ────────────────────────────────────────────────────────
        "gas bill", "piped gas", "gas supply", "gas connection",
        "igl", "mgl", "adani gas", "mahanagar gas",
        "indane gas", "hp gas", "bharat gas", "lpg bill", "lpg cylinder",

        // ── Water & municipal ────────────────────────────────────────────────
        "water bill", "water board", "water supply", "water tax",
        "jal board", "bwssb", "pmc water", "nmmc water", "bwsc",

        // ── Telecom bills (postpaid / landline / broadband) ──────────────────
        "postpaid bill", "mobile bill", "broadband bill",
        "internet bill", "landline bill", "bsnl bill", "mtnl",
        "airtel bill", "jio bill", "vi bill", "vodafone bill",

        // ── DTH ──────────────────────────────────────────────────────────────
        "dth recharge", "dth bill", "dth pack",
        "tataplay", "tata sky", "dish tv", "sun direct", "videocon d2h", "d2h",

        // ── Insurance premiums ───────────────────────────────────────────────
        "insurance premium", "life insurance premium", "lic premium",
        "policy premium", "health insurance premium",
        "motor insurance premium", "vehicle insurance premium",
        "bajaj allianz premium", "star health premium",

        // ── Property & society maintenance ───────────────────────────────────
        "property tax", "house tax", "municipal tax",
        "society maintenance", "maintenance charges", "flat maintenance",
        "housing society", "apartment maintenance",

        // ── Government taxes & statutory payments ────────────────────────────
        "income tax", "income-tax", "itns 280", "income tax challan",
        "income tax dept", "advance tax", "self assessment tax",
        "tds payment", "tds challan", "tin-nsdl", "oltas",
        "gst payment", "gst challan", "gst portal", "goods and services tax",
        "professional tax", "pt payment",
        "stamp duty", "registration fee", "mca filing",

        // ── Generic utility ──────────────────────────────────────────────────
        "utility"
    )

    /**
     * Matched against the normalized merchant name extracted from SMS.
     * Add keywords here as you discover more merchants in your own messages.
     */
    val MERCHANT_RULES: List<Pair<List<String>, Category>> = listOf(

        // ── Food & Dining ────────────────────────────────────────────────────
        listOf(
            // Delivery platforms & parent companies
            "swiggy", "zomato", "dunzo", "eatsure", "rebel foods",
            "bundl", "bundl tech",              // Swiggy's parent
            "eternal",                          // Zomato's parent (Eternal Ltd)
            "swiggy genie",
            // Cloud kitchen brands
            "faasos", "behrouz", "oven story", "mandarin oak",
            "box8", "freshmenu", "lunchbox", "eat club", "eatclub",
            // Cafes & desserts
            "starbucks", "tata starbucks",
            "costa coffee", "cafe coffee day", "ccd", "barista",
            "chaayos", "sunshine teahouse",     // Chaayos legal name
            "theobroma", "keventers", "la pino",
            // QSR chains — brand + parent/legal entity names
            "dominos", "domino", "jubilant foodworks", "jubilant food",  // Domino's parent
            "pizza hut", "yum restaurants", "yum! restaurants",          // Pizza Hut parent
            "mcdonalds", "mcdonald", "hardcastle restaurants",           // McDonald's W&S parent
            "connaught plaza restaurants",                               // McDonald's N&E parent
            "kfc", "devyani international",                              // KFC parent
            "burger king", "restaurant brands asia",                     // Burger King parent
            "subway", "wendy", "taco bell", "papa john",
            "wow momo", "wow momo foods",
            "mojo pizza", "pizza rush",
            // Casual dining & restaurants
            "haldirams", "haldiram", "haldiram snacks",
            "barbeque nation", "barbeque", "bbq nation",
            "biryani blues", "paradise biryani", "behrouz biryani",
            "social", "impresario",                                      // Social parent
            "the beer cafe", "hard rock cafe",
            "saravana bhavan", "sagar ratna", "punjab grill",
            "mainland china", "oh calcutta", "speciality restaurants",   // Speciality Restaurants parent
            // Generic
            "restaurant", "cafe", "dhaba", "biryani"
        ) to Category.FOOD,

        // ── Grocery & Supermarket ─────────────────────────────────────────────
        listOf(
            // Online grocery & quick commerce
            "bigbasket", "big basket", "grofers", "blinkit", "blink c", "rsp*blink", "blink",
            "zepto", "instamart", "swiggy instamart",
            "jiomart", "jio mart", "amazon fresh", "flipkart supermart",
            "milkbasket", "supr daily", "daily basket", "country delight",
            // Supermarket chains — brand + parent/legal entity names
            "dmart", "d-mart", "avenue supermart", "avenue supe",       // DMart
            "star bazaar", "trent hypermarket",                         // Star Bazaar parent
            "reliance fresh", "reliance smart", "smart bazaar",
            "big bazaar", "future retail",                              // Big Bazaar parent
            "more supermarket", "more retail",
            "spencers", "rpsg retail",                                  // Spencer's parent
            "nature basket", "natures basket", "godrej natures basket",
            "nilgiris", "heritage fresh", "easyday",
            "lulu hypermarket", "lulu international",                   // Lulu parent
            "spar", "max hypermarkets",                                 // Spar parent
            "walmart", "walmart india", "best price",                   // Walmart/Best Price
            "metro cash", "metro wholesale",                            // Metro C&C
            // Generic
            "grocery", "kirana", "supermarket", "hypermarket"
        ) to Category.GROCERY,

        // ── Commute & Local Transport ─────────────────────────────────────────
        listOf(
            // Ride-hailing
            "uber", "ola", "rapido", "meru", "savaari", "indrive", "in drive",
            "blusmart", "blu smart",
            // Public transport
            "metro", "dmrc", "bmtc", "best bus", "ksrtc", "msrtc", "gsrtc",
            "tsrtc", "pmpml", "cmrl", "nmmt",
            "hyderabad metro", "nagpur metro", "kochi metro",
            // Fuel stations
            "petrol", "diesel", "fuel", "cng",
            "hp petrol", "hpcl", "iocl", "bpcl", "shell", "nayara", "essar",
            "indian oil", "bharat petroleum", "hindustan petroleum",
            // Parking & tolls
            "parking", "fastag", "netc fastag", "toll", "park plus", "parkplus",
            // Micro-mobility
            "yulu", "bounce", "vogo", "ola electric", "ather"
        ) to Category.COMMUTE,

        // ── Travel ───────────────────────────────────────────────────────────
        listOf(
            // Airlines — brand + legal entity names
            "indigo", "interglobe aviation",                             // IndiGo parent
            "spicejet", "air india", "vistara", "akasa",
            "goair", "air asia", "air india express", "blue dart",
            // Railways
            "irctc", "indian railway", "indianrailway", "rail",
            // Travel booking portals
            "makemytrip", "mmt", "goibibo", "yatra", "cleartrip",
            "easemytrip", "ixigo", "abhibus",
            // Hotels & stays — brand + parent names
            "oyo", "oravel stays",                                       // OYO parent
            "treebo", "zostel", "fabhotels", "lemon tree",
            "taj hotel", "itc hotel", "marriott", "hyatt", "radisson",
            "holiday inn", "novotel", "ibis", "crowne plaza",
            "the leela", "sarovar", "ginger hotel",
            "club mahindra", "sterling resorts",
            // Bus & cab for travel
            "redbus", "abhibus", "airport", "taxi"
        ) to Category.TRAVEL,

        // ── Entertainment ─────────────────────────────────────────────────────
        // NOTE: kept BEFORE Shopping so "amazon prime" / "prime video" match
        // here before the generic "amazon" in Shopping.
        listOf(
            // OTT streaming
            "netflix", "hotstar", "disney", "disney+", "jiocinema", "jio cinema",
            "prime video", "amazon prime", "zee5", "zee 5",
            "sony liv", "sonyliv", "voot", "alt balaji", "altbalaji",
            "eros now", "lionsgate", "hungama", "mubi",
            "sun nxt", "discovery plus", "paramount",
            // Music streaming
            "spotify", "gaana", "jiosaavn", "jio saavn", "wynk",
            "apple music", "youtube premium", "youtube music",
            // OTT parent/legal names that appear in SMS
            "novi digital",                                              // Disney+Hotstar parent
            "sony pictures networks", "spni",                           // Sony LIV parent
            // Cinema & events
            "bookmyshow", "book my show", "bigtree entertainment",      // BookMyShow parent
            "paytm insider", "ticketnew",
            "pvr", "inox", "pvr inox", "inox leisure",                  // PVR INOX parent
            "cinepolis", "carnival cinemas",
            "zomato district", "district",
            // Gaming
            "steam", "playstation", "xbox", "google play", "epic games",
            "mpl", "dream11", "winzo",
            // Generic
            "gaming"
        ) to Category.ENTERTAINMENT,

        // ── Shopping & E-commerce ─────────────────────────────────────────────
        listOf(
            // E-commerce platforms
            "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho",
            "snapdeal", "paytm mall", "tata cliq", "shopsy", "tatacliq",
            "purplle", "firstcry", "first cry", "lenskart",
            // Electronics retail — brand + parent
            "croma", "infiniti retail",                                  // Croma parent (Tata)
            "reliance digital",
            "vijay sales",
            "poorvika", "poorvika mobiles",
            "sangeetha", "sangeetha mobiles",
            // Fashion & apparel — brand + parent/legal entity
            "zara", "inditex trent",                                     // Zara parent
            "h&m", "h and m", "hennes mauritz",                         // H&M
            "zudio", "westside", "trent limited",                       // Zudio + Westside parent
            "pantaloons", "allen solly", "peter england",
            "van heusen", "louis philippe",
            "aditya birla fashion", "abfrl",                            // AB Fashion parent
            "mango", "dlf brands",                                      // Mango parent
            "marks spencer", "marks and spencer", "m&s reliance",
            "levi", "levi strauss",                                     // Levi's
            "lifestyle", "max fashion", "max store",
            "landmark group",                                            // Lifestyle + MAX parent
            "shoppers stop", "shopper stop",
            "reliance trends",
            "v-mart", "vmart", "v mart", "vmart retail",
            "cantabil", "cantabil retail",
            "brand factory",
            "fabindia", "fab india", "khadi", "biba",
            "and fashion", "w brand", "aurelia", "global desi",
            // Footwear
            "bata", "bata india", "mochi", "metro shoes", "metro brands",
            "woodland", "aero club",                                     // Woodland parent
            "liberty shoes",
            // Sporting goods
            "decathlon",
            // Home & furniture
            "ikea", "ikea india", "inter ikea",
            "pepperfry", "urban ladder", "hometown", "home town",
            "nilkamal", "godrej interio", "godrej boyce",
            "pepperfry", "asian paints", "berger paints", "nerolac",
            // Mattress & home
            "sleepwell", "sheela foam",
            "wakefit", "duroflex", "springfit",
            // Beauty & personal care
            "body shop", "quest retail",                                 // Body Shop parent
            "forest essentials",
            "kama ayurveda",
            "mamaearth", "honasa consumer",                             // Mamaearth parent
            "wow skin", "body cupid",                                   // WOW Skin parent
            "lotus herbals",
            // Jewellery
            "tanishq", "malabar gold", "joyalukkas", "senco gold", "kalyan jewellers",
            "png jewellers", "tbz", "tribhovandas"
        ) to Category.SHOPPING,

        // ── Bills, Utilities & Subscriptions ─────────────────────────────────
        listOf(
            // Electricity (specific names — broader patterns in UTILITY_PRIORITY_KEYWORDS)
            "electricity", "bses", "tata power", "adani electricity", "msedcl",
            "bescom", "tneb", "wbsedcl", "jdvvnl", "torrent power",
            // Gas
            "piped gas", "igl", "mgl", "adani gas",
            // Internet & broadband
            "broadband", "wifi", "act fibernet", "act broadband",
            "hathway", "gtpl", "you broadband", "excitel",
            "jio fiber", "jio fibre", "airtel fiber", "airtel fibre",
            "airtel xstream", "airtel broadband",
            // Mobile / telecom
            "jio", "airtel", "vodafone", "vi ", "bsnl", "mtnl", "recharge",
            // DTH
            "dth", "tata play", "tataplay", "tata sky", "tatasky",
            "dish tv", "sun direct", "videocon d2h", "d2h",
            // Home services
            "urban company", "urbancompany", "urbanclap", "urban clap",
            "sulekha", "justdial",
            // Insurance premiums
            "insurance", "lic", "hdfc life", "icici prudential", "sbi life",
            "max life", "bajaj allianz", "star health", "care health",
            "niva bupa", "digit insurance", "tata aig", "sbi general",
            "new india assurance", "policy bazaar", "policybazaar",
            // Municipal / housing
            "municipal", "property tax", "maintenance", "society",
            "nobroker", "no broker",
            // SaaS subscriptions
            "anthropic", "microsoft 365", "office 365", "google workspace",
            "adobe", "canva", "dropbox", "zoom subscription",
            "github", "notion", "slack"
        ) to Category.BILLS,

        // ── Education ────────────────────────────────────────────────────────
        listOf(
            // AI / Tech education
            "anthropic",
            // EdTech platforms
            "byju", "byjus", "vedantu", "unacademy", "khan academy",
            "whitehat", "toppr", "testbook", "gradeup", "pw ", "physics wallah",
            "simplilearn", "upgrad", "edureka", "skillshare",
            "udemy", "coursera", "linkedin learning", "pluralsight",
            "great learning", "emeritus", "alison",
            // Offline coaching
            "allen", "aakash", "resonance", "fiitjee", "career launcher",
            "t.i.m.e", "time institute", "ims ", "cl ",
            // Language
            "duolingo", "babbel",
            // Generic
            "school", "college", "university", "fees", "tuition",
            "books", "stationery", "exam", "coaching", "course"
        ) to Category.EDUCATION,

        // ── Health & Medical ─────────────────────────────────────────────────
        listOf(
            // Online pharmacy & health apps
            "netmeds", "1mg", "tata 1mg", "pharmeasy", "apollo pharmacy",
            "medplus", "guardian pharmacy", "frank ross",
            "healthkart", "himalaya",
            // Telemedicine & diagnostics
            "practo", "lybrate", "mfine", "docsapp",
            "thyrocare", "dr lal", "healthians", "redcliffe labs",
            "orange health", "vijaya diagnostic", "metropolis", "srl",
            // Hospitals
            "apollo", "fortis", "manipal", "max hospital", "narayana",
            "aster", "kims", "rainbow hospital", "cloudnine",
            // Generic medical keywords
            "pharmacy", "medical", "hospital", "clinic", "doctor",
            "lab", "diagnostic", "ayurvedic", "dental", "optician", "eye care",
            "pathology", "nursing home", "health insurance", "mediassist"
        ) to Category.HEALTH,

        // ── Fitness & Sports ─────────────────────────────────────────────────
        listOf(
            // Fitness apps & gym chains
            "cult.fit", "curefit", "cure.fit", "gold gym", "anytime fitness",
            "fitternity", "fitpass", "fitnessworld",
            // Sports court & activity booking
            "playo", "playopgonline", "khelo", "sportobuddy", "sporthood",
            "spintly", "hudle", "sportz village",
            // Swimming, yoga, martial arts
            "swimming", "aquatica", "swim",
            "yoga", "zumba", "crossfit", "pilates",
            // Generic fitness/sports keywords
            "gym", "fitness center", "sports club", "sports academy",
            "badminton", "tennis", "cricket academy", "football academy",
            "squash", "basketball", "cycling club"
        ) to Category.FITNESS,

        // ── Income markers ───────────────────────────────────────────────────
        listOf(
            "salary", "payroll", "stipend", "freelance", "dividend",
            "interest", "rent received", "neft credit", "imps credit",
            "cashback", "refund", "reversal"
        ) to Category.INCOME,

        // ── Investment platforms & brokers ───────────────────────────────────
        listOf(
            // Trading & investment apps
            "zerodha", "groww", "upstox", "kuvera", "paytm money",
            "angel broking", "angel one", "5paisa", "icicidirect",
            "hdfc securities", "kotak securities", "motilal oswal",
            "sharekhan", "nj wealth", "fundsindia", "mfcentral",
            "scripbox", "indmoney", "et money", "etmoney", "smallcase",
            "wazirx", "coinswitch", "coindcx",
            // Generic investment terms (longer strings only — "rd "/"fd " removed)
            "mutual fund", "sip", "ppf",
            "fixed deposit", "recurring deposit"
        ) to Category.INVESTMENT,

        // ── Self-transfers ───────────────────────────────────────────────────
        listOf(
            "self", "own account", "transfer to self", "neft to self"
        ) to Category.TRANSFER
    )

    /**
     * Fallback: matched against the full SMS body when merchant is unknown.
     */
    val BODY_RULES: List<Pair<List<String>, Category>> = listOf(
        listOf("salary", "payroll") to Category.INCOME,
        listOf("refund", "cashback", "reversal") to Category.INCOME,
        listOf("recharge", "dth", "electricity") to Category.BILLS,
        listOf("fuel", "petrol", "diesel", "cng") to Category.COMMUTE,
        // "atm" as standalone fallback — if it reaches here it's still clearly cash
        listOf("atm") to Category.CASH
    )
}
