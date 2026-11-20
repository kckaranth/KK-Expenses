package org.totschnig.myexpenses.util

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.prefHandler
import org.totschnig.myexpenses.preference.PrefKey
import java.math.BigDecimal
import java.util.*

class CurrencyFormatterTest {
    private lateinit var currencyFormatter: CurrencyFormatter
    private lateinit var application: MyApplication

    @Before
    fun setUp() {
        whenever(prefHandler.getString(eq(PrefKey.CUSTOM_DECIMAL_FORMAT), any())).thenReturn("")
        application = mock()
        currencyFormatter = CurrencyFormatter(prefHandler, application)
    }

    @Test
    fun testUsdFractionDigitsAndNegativeAmount() {
        whenever(application.userPreferredLocale).thenReturn(Locale.US)
        val usd = CurrencyUnit("USD", "$", 2)

        assertThat(currencyFormatter.formatCurrency(BigDecimal("1234.5"), usd))
            .isEqualTo("$1,234.50")
        assertThat(currencyFormatter.formatCurrency(BigDecimal("-12.34"), usd))
            .isEqualTo("−$12.34")
    }

    @Test
    fun testZeroFractionCurrencyDoesNotDisplayDecimals() {
        whenever(application.userPreferredLocale).thenReturn(Locale.US)
        val jpy = CurrencyUnit("JPY", "¥", 0)

        assertThat(currencyFormatter.formatCurrency(BigDecimal("1234"), jpy))
            .isEqualTo("¥1,234")
    }

    @Test
    fun testThreeFractionCurrencyPreservesPrecision() {
        whenever(application.userPreferredLocale).thenReturn(Locale.US)
        val kwd = CurrencyUnit("KWD", "KD", 3)

        val formatted = currencyFormatter.formatCurrency(BigDecimal("1.234"), kwd)
        assertThat(formatted).contains("1.234")
    }

    @Test
    fun testMoneyFormatGermany() {
        val eur = CurrencyUnit("EUR", "€", 2)
        whenever(application.userPreferredLocale).thenReturn(Locale.GERMANY)

        val javaVersion = System.getProperty("java.version")!!.split('.')[0].toInt()
        //newer Java version uses non-breaking space
        assertThat(currencyFormatter.formatMoney(Money(eur, 150))).isEqualTo(if (javaVersion >= 10) "1,50 €" else "1,50 €")
    }
}