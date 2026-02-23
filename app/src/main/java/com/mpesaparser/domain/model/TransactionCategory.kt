package com.mpesaparser.domain.model

enum class TransactionCategory(val label: String) {
    RECEIVED_MONEY("Received Money"),
    SEND_MONEY("Send Money"),
    PAY_BILL("Pay Bill"),
    BUY_GOODS("Buy Goods"),
    AIRTIME("Airtime"),
    BUNDLE("Bundles"),
    AGENT_DEPOSIT("Agent Deposit"),
    AGENT_WITHDRAWAL("Agent Withdrawal"),
    LOAN("Loan / Credit"),
    CHARGE("Charges"),
    BUSINESS("Business"),
    OVERDRAFT("Overdraft"),
    OTHER("Other")
}
