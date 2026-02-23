package com.mpesaparser.utils

import android.content.Context
import com.mpesaparser.domain.model.ScoringConfig

class ScoringConfigStore {

    companion object {
        private const val PREFS = "mpesa_scoring_config"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_CASHFLOW = "w_cashflow"
        private const val KEY_LIQUIDITY = "w_liquidity"
        private const val KEY_RELIABILITY = "w_reliability"
        private const val KEY_INCOME = "w_income"
        private const val KEY_EXPENSE = "w_expense"
        private const val KEY_OBLIGATION = "w_obligation"
        private const val KEY_DIVERSIFICATION = "w_diversification"
        private const val KEY_HIGHVALUE = "w_highvalue"
    }

    fun load(context: Context): ScoringConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ScoringConfig(
            highValueThreshold = prefs.getFloat(KEY_THRESHOLD, 5000f).toDouble(),
            cashflowWeight = prefs.getInt(KEY_CASHFLOW, 22),
            liquidityWeight = prefs.getInt(KEY_LIQUIDITY, 20),
            reliabilityWeight = prefs.getInt(KEY_RELIABILITY, 15),
            incomeStabilityWeight = prefs.getInt(KEY_INCOME, 10),
            expenseStabilityWeight = prefs.getInt(KEY_EXPENSE, 8),
            obligationWeight = prefs.getInt(KEY_OBLIGATION, 10),
            diversificationWeight = prefs.getInt(KEY_DIVERSIFICATION, 7),
            highValueBehaviorWeight = prefs.getInt(KEY_HIGHVALUE, 8)
        ).normalized()
    }

    fun save(context: Context, config: ScoringConfig) {
        val normalized = config.normalized()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_THRESHOLD, normalized.highValueThreshold.toFloat())
            .putInt(KEY_CASHFLOW, normalized.cashflowWeight)
            .putInt(KEY_LIQUIDITY, normalized.liquidityWeight)
            .putInt(KEY_RELIABILITY, normalized.reliabilityWeight)
            .putInt(KEY_INCOME, normalized.incomeStabilityWeight)
            .putInt(KEY_EXPENSE, normalized.expenseStabilityWeight)
            .putInt(KEY_OBLIGATION, normalized.obligationWeight)
            .putInt(KEY_DIVERSIFICATION, normalized.diversificationWeight)
            .putInt(KEY_HIGHVALUE, normalized.highValueBehaviorWeight)
            .apply()
    }
}
