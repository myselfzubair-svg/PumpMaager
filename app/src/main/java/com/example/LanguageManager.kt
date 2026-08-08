package com.example

enum class AppLanguage {
    English,
    Hindi
}

object LanguageManager {
    var currentLanguage = AppLanguage.English

    fun translate(enText: String, hiText: String): String {
        return if (currentLanguage == AppLanguage.English) enText else hiText
    }
    
    // Welcome Screen
    val welcomeTitle get() = translate("Welcome to\nD R Inamdar Petroleum", "D R Inamdar Petroleum\nमें आपका स्वागत है")
    val next get() = translate("Next", "आगे बढ़ें")
    val poweredBy get() = translate("powered to you by", "द्वारा संचालित")
    val selectTheme get() = translate("Personalize App Theme", "ऐप थीम का रूप बदलें")
    val selectThemeDesc get() = translate("Choose a professional design skin for your pump session", "अपने ऑडिट सत्र के लिए एक शानदार रंग और थीम चुनें")
    val themeRoyal get() = translate("Royal Indigo", "शाही ब्लू (Indigo)")
    val themeTeal get() = translate("Eco Teal", "इको सफारी (Teal)")
    val themeCrimson get() = translate("Warm Crimson", "क्रिमसन सैंड (Crimson)")
    val themeMono get() = translate("Sleek Ivory", "मूल स्किन-टोन (Sleek)")
    
    // Nozzle Selection
    val calculationMode get() = translate("Calculation Mode", "कैलकुलेशन मोड")
    val selectNozzleCount get() = translate("Select Nozzle Count", "नोज़ल की संख्या चुनें")
    val chooseConfiguration get() = translate("Choose the pump configuration for this session to proceed with auditing.", "ऑडिटिंग शुरू करने के लिए कृपया पंप कॉन्फ़िगरेशन चुनें।")
    val selectionBackDesc get() = translate("Back to Welcome Screen", "वेलकम स्क्रीन पर जाएं")
    val nozzle2Calc get() = translate("2 Nozzle Calculation", "2 नोज़ल कैलकुलेशन")
    val nozzle2Desc get() = translate("Auditing for single-product or dual nozzle pumps.", "सिंगल-उत्पाद या डुअल नोज़ल पंपों के लिए ऑडिट।")
    val nozzle4Calc get() = translate("Shift Report & Tally", "शिफ्ट रिपोर्ट और मिलान (Tally)")
    val nozzle4Desc get() = translate("Full pump shift report calculations, nozzle labels and test deductions.", "नोज़ल लेबल और टेस्टिंग कटौती के साथ पूर्ण पंप शिफ्ट कैलकुलेशन और मिलान।")
    val fullDayCalc get() = translate("Full Day Calculation", "पूरे दिन का कैलकुलेशन")
    val fullDayDesc get() = translate("Shift report sales audit with mobilization, store sales and complete cash desk tally.", "मोबिलाइजेशन, स्टोर बिक्री और पूर्ण कैश डेस्क मिलान के साथ शिफ्ट रिपोर्ट ऑडिट।")

    // CA Module Config Workflow
    val caConfigTitle get() = translate("CA Module Configuration", "CA मॉड्यूल कॉन्फ़िगरेशन")
    val stepCaName get() = translate("Step 1: CA Name", "चरण 1: सीए (CA) नाम")
    val caNameAutoFilled get() = translate("(Auto-filled)", "(स्वचालित रूप से भरा गया)")
    val stepSelectDate get() = translate("Step 2: Select Date", "चरण 2: तारीख चुनें")
    val calculationDate get() = translate("Calculation Date", "कैलकुलेशन की तारीख")
    val stepSelectNozzles get() = translate("Step 3: Select Nozzles", "चरण 3: नोज़ल चुनें")
    val selectNozzlesDesc get() = translate("Select active nozzles assigned to your account for this shift.", "इस शिफ्ट के लिए अपने खाते में सौंपे गए सक्रिय नोज़ल चुनें।")
    val noNozzlesFound get() = translate("No nozzles have been configured. Please complete the station setup before starting CA calculations.", "कोई नोज़ल कॉन्फ़िगर नहीं किया गया है। कृपया सीए कैलकुलेशन शुरू करने से पहले स्टेशन सेटअप पूरा करें।")
    val errorLoadingNozzles get() = translate("Unable to load configured nozzles. Please check your connection or contact the administrator.", "कॉन्फ़िगर किए गए नोज़ल लोड करने में असमर्थ। कृपया अपना कनेक्शन जांचें या व्यवस्थापक से संपर्क करें।")
    val startCalculation get() = translate("START CALCULATION", "कैलकुलेशन शुरू करें")
    val selectAll get() = translate("Select All", "सभी चुनें")
    val clearSelection get() = translate("Clear Selection", "चयन साफ़ करें")
    val searchNozzles get() = translate("Search nozzles by name, tank or product...", "नाम, टैंक या उत्पाद द्वारा नोज़ल खोजें...")

    // Audit Details
    val audit2Config get() = translate("2-Nozzle Audit Config", "2-नोज़ल ऑडिट कॉन्फ़िगरेशन")
    val audit4Config get() = translate("Shift Report & Tally Config", "शिफ्ट रिपोर्ट और मिलान कॉन्फ़िगरेशन")
    val auditSessionInit get() = translate("AUDIT SESSION INITIALIZATION", "ऑडिट सत्र का प्रारंभ")
    val recordDetailsText get() = translate("Kindly record the shift date, cashier name, and active pump meter number to initialize the shift audit ledger.", "शिफ्ट ऑडिट लेजर शुरू करने के लिए कृपया तारीख, कैशियर का नाम और मीटर नंबर दर्ज करें।")
    val auditDate get() = translate("Audit Date", "ऑडिट की तारीख")
    val caNameLabel get() = translate("Customer Assistant / Cashier (CA) Name", "कैशियर / सीए (CA) का नाम")
    val caNamePlaceholder get() = translate("Enter cashier name", "कैशियर का नाम दर्ज करें")
    val meterNumber get() = translate("Meter Number", "मीटर नंबर")
    val caNameError get() = translate("Please enter the CA name to proceed", "आगे बढ़ने के लिए कृपया कैशियर का नाम दर्ज करें")
    val proceedToCalculator get() = translate("PROCEED TO CALCULATOR", "कैलकुलेटर पर जाएं")
    
    // Common Calculator Labels
    val salesReconciliationTitle get() = translate("SALES RECONCILIATION SUMMARY (MS+HSD)", "बिक्री मिलान सारांश (MS+HSD)")
    val totalSalesMsHsd get() = translate("Total Sales (MS+HSD)", "कुल बिक्री (MS+HSD)")
    val totalNetSaleL get() = translate("Total Net Sale (MS+HSD L)", "कुल शुद्ध बिक्री (MS+HSD लीटर)")
    val phonepeDigital get() = translate("PhonePe / Digital Collections", "फोनपे / डिजिटल संग्रह")
    val udhariJama get() = translate("UDHARI JAMA (ADD)", "उधारी जमा (जोड़ें)")
    val creditRecovery get() = translate("CREDIT RECOVERY", "उधारी वसूली")
    val udhariJamaDesc get() = translate("Any recovered outstanding credit (Udhari Jama) here will be automatically added to the Sales Grand Total.", "यहाँ दर्ज की गई पुरानी उधारी को सीधे कुल सेल में जोड़ दिया जाएगा।")
    val detailsLabel get() = translate("Details (e.g., Cafe, John)", "विवरण (जैसे, कैफ़े, जॉन)")
    val addBtn get() = translate("ADD", "जोड़ें")
    val expensesKharch get() = translate("EXPENSES / KHARCH (LESS)", "दैनिक खर्च (घटाएं)")
    val dailyExpenses get() = translate("DAILY EXPENSES", "दैनिक खर्च")
    val kharchDesc get() = translate("Any cash spent on daily station operations (Expenses / Kharch) will be subtracted from Expected Net Cash.", "स्टेशन के दैनिक खर्चों को कुल अपेक्षित नकद राशि में से घटाया जाएगा।")
    val expensesLabel get() = translate("Expense Details (e.g., Tea, Food)", "खर्च का विवरण (जैसे, चाय, भोजन)")
    val amountRs get() = translate("Amount (₹)", "राशि (₹)")
    val creditUdhar get() = translate("CREDIT / UDHAR (LESS)", "दैनिक उधार (घटाएं)")
    val dailyFuelDebt get() = translate("DAILY FUEL DEBT", "दैनिक ईंधन ऋण")
    val udharDesc get() = translate("Any fuel supplied on credit (Udhar) or local account supply will be subtracted from Expected Net Cash.", "उधार पर दिए गए ईंधन (क्रेडिट) को कुल अपेक्षित नकद राशि में से घटाया जाएगा।")
    val udharLabel get() = translate("Debt Details (e.g., Bus, Police)", "ऋण का विवरण (जैसे, बस, पुलिस)")
    val physicalCash get() = translate("PHYSICAL CASH IN HAND", "हाथ में उपलब्ध नकद राशि")
    val enterCountValue get() = translate("Enter Note Count or Value", "नोटों की संख्या या मूल्य दर्ज करें")
    val denoCoins get() = translate("Coins Value (₹)", "सिक्कों का मूल्य (₹)")
    val summaryOfSales get() = translate("SUMMARY OF SALES (MS+HSD)", "बिक्री का सारांश (MS+HSD)")
    
    // Audit Status/Results
    val expectedNetCash get() = translate("Expected Net Cash", "अपेक्षित शुद्ध नकद")
    val actualCashInHand get() = translate("Actual Cash in Hand", "हाथ में उपलब्ध नकद")
    val cashDiscrepancy get() = translate("Cash Discrepancy (Farak)", "नकद अंतर (फरक)")
    val shiftSalesTally get() = translate("SHIFT SALES TALLY (DISCREPANCY)", "शिफ्ट बिक्री टीली (लाभांश / फरक)")
    val tallyPerfect get() = translate("TALLY PERFECT", "बिल्कुल सही मिलान")
    val cashShortage get() = translate("CASH SHORTAGE", "नकद कमी (शॉर्टेज)")
    val cashSurplus get() = translate("CASH SURPLUS", "नकद अधिशेष (सरप्लस)")
    
    // Bottom Action Bar
    val clearAllFields get() = translate("Clear All", "सभी साफ करें")
    val printReport get() = translate("Print Report", "प्रिंट रिपोर्ट")
    val resetFields get() = translate("Reset Fields", "रिसेट करें")
    
    // Full Day specific
    val netMsSales get() = translate("Net MS Petrol Sales:", "शुद्ध MS पेट्रोल बिक्री:")
    val netHsdSales get() = translate("Net HSD Diesel Sales:", "शुद्ध HSD डीजल बिक्री:")
    val totalReconciled get() = translate("TOTAL RECONCILED FUEL SALES:", "कुल मिलाई गई ईंधन बिक्री:")
}

fun t(en: String, hi: String): String = LanguageManager.translate(en, hi)
