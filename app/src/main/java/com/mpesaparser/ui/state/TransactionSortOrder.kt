package com.mpesaparser.ui.state

enum class TransactionSortOrder(val label: String) {
    DATE_DESC("Newest"),
    DATE_ASC("Oldest"),
    AMOUNT_DESC("Amount High"),
    AMOUNT_ASC("Amount Low")
}
