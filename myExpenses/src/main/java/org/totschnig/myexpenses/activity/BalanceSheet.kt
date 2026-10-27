package org.totschnig.myexpenses.activity

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.res.ResourcesCompat
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieEntry
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AmountText
import org.totschnig.myexpenses.compose.CheckableMenuEntry
import org.totschnig.myexpenses.compose.ColoredAmountText
import org.totschnig.myexpenses.compose.LocalCurrencyContext
import org.totschnig.myexpenses.compose.LocalDateFormatter
import org.totschnig.myexpenses.compose.PieChartCompose
import org.totschnig.myexpenses.compose.TooltipIconButton
import org.totschnig.myexpenses.compose.TooltipIconMenu
import org.totschnig.myexpenses.compose.conditional
import org.totschnig.myexpenses.compose.filter.ActionButton
import org.totschnig.myexpenses.compose.optional
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.util.epochMillis2LocalDate
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.viewmodel.data.BalanceSection
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FullAccount.Companion.partitionByAccountType
import timber.log.Timber
import java.time.LocalDate
import java.util.Currency
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

@Composable
fun BalanceSheetOptions(
    showHiddenState: MutableState<Boolean>?,
    showZeroState: MutableState<Boolean>?,
    showChartState: MutableState<Boolean>,
    highlight: MutableState<Triple<Boolean, Int, Long?>?>,
    onPrint: () -> Unit
) {
    TooltipIconButton(
        tooltip = stringResource(R.string.menu_print),
        imageVector = Icons.Filled.Print,
        onClick = onPrint
    )
    TooltipIconMenu(
        tooltip = stringResource(R.string.options),
        imageVector = Icons.Filled.Tune,
        menu = listOfNotNull(
            showHiddenState?.let {
                CheckableMenuEntry(
                    label = R.string.show_invisible,
                    isChecked = it.value,
                    command = "TOGGLE_SHOW_INVISIBLE"
                ) {
                    showHiddenState.value = !showHiddenState.value
                }
            },
            showZeroState?.let {
                CheckableMenuEntry(
                    label = R.string.show_zero,
                    isChecked = it.value,
                    command = "TOGGLE_SHOW_ZERO"
                ) {
                    showZeroState.value = !showZeroState.value
                }
            },
            CheckableMenuEntry(
                label = R.string.menu_chart,
                isChecked = showChartState.value,
                command = "TOGGLE_CHART_BALANCE"
            ) {
                showChartState.value = !showChartState.value
                highlight.value = null
            }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheetView(
    modifier: Modifier = Modifier,
    accounts: List<FullAccount>,
    debtSum: Long = 0L,
    date: LocalDate = LocalDate.now(),
    onClose: () -> Unit = {},
    onNavigate: (FullAccount) -> Unit = {},
    onSetDate: (LocalDate) -> Unit = {},
    onPrint: () -> Unit = {},
    showHiddenState: MutableState<Boolean>,
    showZeroState: MutableState<Boolean>,
    showChartState: MutableState<Boolean>,
) {

    Column(modifier) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        //Triple of asset or liability, position in section, index of account or null for Debts
        val highlight = remember { mutableStateOf<Triple<Boolean, Int, Long?>?>(null) }
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Text(text = stringResource(R.string.balance_sheet))
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close"
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            actions = {
                BalanceSheetOptions(
                    showHiddenState.takeIf { accounts.any { !it.isVisible } },
                    showZeroState.takeIf { accounts.any { it.effectiveBalance == 0L } },
                    showChartState,
                    highlight,
                    onPrint,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors()
        )

        BalanceSheetViewInner(
            accounts = accounts,
            debtSum = debtSum,
            date = date,
            onSetDate = onSetDate,
            showChart = showChartState.value,
            highlight = highlight,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
            showHidden = showHiddenState.value,
            showZero = showZeroState.value,
            onNavigate = onNavigate,
            bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.BalanceSheetViewInner(
    accounts: List<FullAccount>,
    debtSum: Long,
    date: LocalDate,
    onSetDate: (LocalDate) -> Unit,
    showChart: Boolean,
    highlight: MutableState<Triple<Boolean, Int, Long?>?>,
    nestedScrollConnection: NestedScrollConnection? = null,
    showHidden: Boolean,
    showZero: Boolean,
    onNavigate: (FullAccount) -> Unit,
    bottomPadding: Dp,
    scrollResetKey: Int = 0,
    onListScrollingChanged: (Boolean) -> Unit = {},
) {

    val horizontalPadding = dimensionResource(R.dimen.padding_main_screen)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Text(
        modifier = Modifier
            .padding(end = horizontalPadding)
            .clickable {
                datePickerState.selectedDateMillis = date.toEpoch() * 1000
                showDatePicker = true
            }
            .align(Alignment.End),
        text = LocalDateFormatter.current.format(date),
        textDecoration = TextDecoration.Underline
    )
    if (showDatePicker) {
        Popup(
            properties = PopupProperties(focusable = true),
            onDismissRequest = {
                showDatePicker = false
            }
        ) {

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 64.dp)
                    .shadow(elevation = 4.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                val maxWidth = this.maxWidth
                val isNarrow = maxWidth < 360.dp
                if (isNarrow && datePickerState.displayMode == DisplayMode.Picker) {
                    Box(
                        modifier = Modifier.requiredSizeIn(
                            minWidth = 360.dp,
                            minHeight = 568.dp
                        )
                    ) {
                        DatePicker(
                            modifier = Modifier.scale(maxWidth / 360.dp),
                            state = datePickerState
                        )
                    }
                } else {
                    DatePicker(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        state = datePickerState
                    )
                }
                ActionButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    hintText = stringResource(R.string.confirm),
                    icon = Icons.Filled.Done
                ) {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let {
                        onSetDate(epochMillis2LocalDate(it))
                    }
                }
            }
        }
    }

    val (assets, totalAssets, liabilities, totalLiabilities) = accounts.partitionByAccountType()

    val assetsWithDebts = totalAssets + (debtSum.takeIf { it > 0L } ?: 0L)
    val liabilitiesWithDebts = totalLiabilities + (debtSum.takeIf { it < 0L } ?: 0L)
    val ratio = if (assetsWithDebts > 0L && liabilitiesWithDebts < 0L)
        assetsWithDebts.toFloat() / -liabilitiesWithDebts else 1f
    val angles = if (ratio > 1f) 360f to 360f / ratio else
        360f * ratio to 360f
    val chartAccountColors = remember(accounts) {
        (
            assets.flatMap { it.accounts } +
            liabilities.flatMap { it.accounts }
        )
            .map { it.id }
            .distinct()
            .sorted()
            .mapIndexed { index, id ->
                id to balanceSheetChartColors[
                    index % balanceSheetChartColors.size
                ]
            }
            .toMap()
    }

    val listState = rememberLazyListState()

    LaunchedEffect(showChart, scrollResetKey) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                onListScrollingChanged(scrolling)
            }
    }

    val chartContent: @Composable (Modifier) -> Unit = { modifier ->

            Box(
                modifier = modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                RenderChart(
                    modifier = Modifier
                        .fillMaxSize(0.95f)
                        .align(Alignment.Center),
                    inner = false,
                    accounts = assets.flatMap { it.accounts },
                    debts = debtSum.takeIf { it > 0 },
                    highlight = highlight,
                    accountColors = chartAccountColors,
                    angle = angles.first,
                )
                RenderChart(
                    modifier = Modifier
                        .fillMaxSize(0.75f)
                        .align(Alignment.Center),
                    inner = true,
                    accounts = liabilities.flatMap { it.accounts },
                    debts = debtSum.takeIf { it < 0 },
                    highlight = highlight,
                    accountColors = chartAccountColors,
                    angle = angles.second,
                )

                val selectedAccount = highlight.value?.let { selected ->
                    val chartAccounts = if (selected.first) {
                        liabilities.flatMap { it.accounts }
                    } else {
                        assets.flatMap { it.accounts }
                    }
                    chartAccounts.firstOrNull {
                        it.id == selected.third
                    }
                }

                val selectedDebt = highlight.value?.let {
                    it.third == null && debtSum != 0L
                } == true

                val centerLabel = when {
                    selectedAccount != null ->
                        selectedAccount.label
                    selectedDebt ->
                        stringResource(R.string.debts)
                    else ->
                        stringResource(
                            R.string.balance_sheet_net_worth
                        )
                }

                val centerLabelStyle =
                    if (centerLabel.length > 20) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.labelMedium
                    }

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.68f),
                    verticalArrangement =
                        androidx.compose.foundation.layout.Arrangement
                            .spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = centerLabel,
                        style = centerLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    ColoredAmountText(
                        amount = when {
                            selectedAccount != null ->
                                selectedAccount.equivalentEffectiveBalance
                            selectedDebt -> debtSum
                            else -> totalAssets +
                                totalLiabilities + debtSum
                        },
                        currency = LocalCurrencyContext.current.homeCurrencyUnit,
                        absolute = false,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    if (
                        selectedAccount != null &&
                        selectedAccount.currencyUnit.code !=
                        LocalCurrencyContext.current.homeCurrencyUnit.code &&
                        selectedAccount.effectiveBalance != 0L
                    ) {
                        AmountText(
                            selectedAccount.effectiveBalance,
                            selectedAccount.currencyUnit,
                            fontSize = 12.sp
                        )
                    }
                }
                  }
            }

    }

    BoxWithConstraints(
        modifier = Modifier.weight(1f)
    ) {
        val isSingleScrollLayout = maxWidth < 600.dp

        LayoutHelper(
        showChart = showChart,
        singleScroll = isSingleScrollLayout,
        chart = chartContent,
        data = { modifier ->



            LazyColumn(
                modifier = modifier
                    .clipToBounds()
                    .optional(nestedScrollConnection) {
                        nestedScroll(it)
                    },
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding + 112.dp
                ),
                state = listState
            ) {
                if (showChart && isSingleScrollLayout) {
                    item(key = "balance_sheet_chart") {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            chartContent(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = 12.dp,
                                        bottom = 16.dp
                                    )
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }

                accountTypeChapter(
                    title = R.string.balance_sheet_section_assets,
                    total = totalAssets,
                    sections = assets,
                    showHidden = showHidden,
                    showZero = showZero,
                    highlight = highlight.value?.third,
                    onNavigate = onNavigate,
                    accountColors = chartAccountColors
                )
                accountTypeChapter(
                    title = R.string.balance_sheet_section_liabilities,
                    total = totalLiabilities,
                    sections = liabilities,
                    showHidden = showHidden,
                    showZero = showZero,
                    highlight = highlight.value?.third,
                    onNavigate = onNavigate,
                    accountColors = chartAccountColors
                )

                if (debtSum != 0L) {
                    item {
                        BalanceSheetSectionHeaderView(
                            name = stringResource(R.string.debts),
                            total = debtSum,
                            highlight = highlight.value != null && highlight.value?.third == null,
                            absolute = false
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                item {
                    BalanceSheetSectionHeaderView(
                        name = stringResource(R.string.balance_sheet_net_worth),
                        total = totalAssets + totalLiabilities + debtSum,
                        absolute = false
                    )
                }
            }

        }
        )
    }
}

fun LazyListScope.accountTypeChapter(
    @StringRes title: Int,
    total: Long,
    sections: List<BalanceSection>,
    showHidden: Boolean,
    showZero: Boolean,
    highlight: Long?,
    onNavigate: (FullAccount) -> Unit,
    accountColors: Map<Long, Int>,
) {
    item {
        BalanceSheetSectionHeaderView(
            name = stringResource(title),
            total = total,
            absolute = false
        )
    }

    sections.forEach {
        accountTypeSection(
            section = it,
            showHidden = showHidden,
            showZero = showZero,
            highlight = highlight,
            onNavigate = onNavigate,
            accountColors = accountColors
        )
    }
    item {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
fun BalanceSheetSectionHeaderView(
    modifier: Modifier = Modifier,
    name: String,
    total: Long,
    highlight: Boolean = false,
    absolute: Boolean = true,
) {
    val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 16.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ColoredAmountText(
                amount = total,
                currency = homeCurrency,
                absolute = absolute,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

fun LazyListScope.accountTypeSection(
    section: BalanceSection,
    showHidden: Boolean,
    showZero: Boolean,
    highlight: Long?,
    onNavigate: (FullAccount) -> Unit,
    accountColors: Map<Long, Int>,
) {
    item {
        val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = section.type.title(LocalContext.current),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ColoredAmountText(
                amount = section.total,
                currency = homeCurrency,
                absolute = false,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    section.accounts
        .filter { (showHidden || it.isVisible) && (showZero || it.effectiveBalance != 0L) }
        .forEach { account ->
            item {
                BalanceAccountItemView(
                    account = account,
                    highlight = highlight == account.id,
                    onNavigate = onNavigate,
                    accentColor = accountColors[account.id]
                )
            }
        }
}

@Composable
fun BalanceAccountItemView(
    account: FullAccount,
    highlight: Boolean,
    onNavigate: (FullAccount) -> Unit,
    accentColor: Int? = null,
) {
    val homeCurrency =
        LocalCurrencyContext.current.homeCurrencyUnit

    val selectedBackground = lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.secondaryContainer,
        0.22f
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable {
                onNavigate(account)
            },
        shape = MaterialTheme.shapes.medium,
        color = if (highlight) {
            selectedBackground
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (highlight) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .width(4.dp)
                        .height(44.dp)
                        .background(
                            color = accentColor?.let {
                                Color(it)
                            } ?: MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        )
                )
            }

            Text(
                text = account.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                horizontalAlignment = Alignment.End
            ) {
                ColoredAmountText(
                    amount = account.equivalentEffectiveBalance,
                    currency = homeCurrency,
                    absolute = false,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (
                    account.currencyUnit.code != homeCurrency.code &&
                    account.effectiveBalance != 0L
                ) {
                    AmountText(
                        account.effectiveBalance,
                        account.currencyUnit,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private val balanceSheetChartColors = listOf(
    0xFF00BFA5.toInt(), // Teal
    0xFF38BDF8.toInt(), // Blue
    0xFFA78BFA.toInt(), // Purple
    0xFFF59E0B.toInt(), // Amber
    0xFFF472B6.toInt(), // Pink
    0xFF34D399.toInt(), // Green
    0xFF818CF8.toInt(), // Indigo
    0xFFFB7185.toInt(), // Coral
    0xFF22D3EE.toInt(), // Cyan
    0xFFEAB308.toInt(), // Yellow
    0xFFC084FC.toInt(), // Violet
    0xFF2DD4BF.toInt()  // Aqua
)

@Composable
fun RenderChart(
    modifier: Modifier,
    inner: Boolean,
    accounts: List<FullAccount>,
    debts: Long?,
    highlight: MutableState<Triple<Boolean, Int, Long?>?>,
    accountColors: Map<Long, Int>,
    angle: Float = 360f,
) {
    val context = LocalContext.current
    val accounts = accounts.filter { it.equivalentEffectiveBalance != 0L }
    val pieEntries = accounts.map { account ->
        PieEntry(
            abs(account.equivalentEffectiveBalance.toFloat()),
            account.label
        )
    }
    val colors = accounts.map { account ->
        accountColors.getValue(account.id)
    }
    PieChartCompose(
        modifier = modifier,
        factory = { ctx ->
            PieChart(ctx)
        },
        holeRadius = if (inner) 75f else 85f,
        angle = angle,
        showEntryLabels = false,
        showSelectionCenterText = false,
        onValueSelected = { index ->
            highlight.value = index?.let { Triple(inner, index, accounts.getOrNull(index)?.id) }
        },
        data = if (debts == null)
            pieEntries else
            pieEntries + PieEntry(
                debts.toFloat().absoluteValue,
                stringResource(R.string.debts)
            ),
        colors = if (debts == null) colors else colors +
                ResourcesCompat.getColor(
                    LocalResources.current,
                    if (inner) R.color.colorExpense else R.color.colorIncome,
                    context.theme
                )
    ) {
        // The Balance Sheet renders Net Worth using a
        // Compose overlay. Suppress MPAndroidChart's
        // own center text to prevent overlapping labels.
        it.centerText = ""

        if (highlight.value?.first == !inner) {
            it.highlightValue(null)
        }
    }
}

@Composable
fun LayoutHelper(
    showChart: Boolean,
    singleScroll: Boolean = false,
    chart: @Composable (Modifier) -> Unit,
    data: @Composable (Modifier) -> Unit,
) {
    if (showChart && singleScroll) {
        data(Modifier.fillMaxSize())
    } else if (showChart) {
        BoxWithConstraints {
            if (this.maxWidth < 600.dp) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    chart(Modifier.fillMaxWidth())

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    data(Modifier.weight(1f))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    chart(Modifier.weight(1f))
                    data(Modifier.weight(1f))
                }
            }
        }
    } else {
        data(Modifier.fillMaxSize())
    }
}

@Preview
@Composable
fun BalanceSheet() {
    BalanceSheetView(
        accounts = listOf(
            FullAccount(
                label = "Checking Account",
                currentBalance = 500000,
                id = 1,
                currencyUnit = CurrencyUnit.DebugInstance,
                type = AccountType.BANK, // $5,000.00 (assuming cents)
            ),
            FullAccount(
                label = "Savings Account",
                type = AccountType.BANK,
                id = 2,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 1250000, // $12,500.00
            ),
            FullAccount(
                label = "Credit Card 1",
                type = AccountType.CCARD,
                id = 3,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = -25000, // -$250.00 (negative balance for debt)
            ),
            FullAccount(
                label = "Credit Card 2",
                type = AccountType.CCARD,
                id = 4,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = -100000, // -$1,000.00
            ),
            FullAccount(
                label = "USD Cash",
                currentBalance = 200000, // €2,000.00
                id = 5,
                type = AccountType.CASH,
                currencyUnit = CurrencyUnit(Currency.getInstance("USD")),
                equivalentCurrentBalance = (200000 * 0.92).roundToLong()
            ),
            FullAccount(
                label = "JPY Account",
                type = AccountType.BANK,
                id = 6,
                currentBalance = 5000000, // ¥50,000.00
                currencyUnit = CurrencyUnit(Currency.getInstance("JPY")),
                equivalentCurrentBalance = (5000000 * 0.0075).roundToLong(),
            ),
            FullAccount(
                label = "USD Invest Account",
                type = AccountType.ASSET,
                id = 7,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 2000000, // $20,000
            ),
            FullAccount(
                label = "GBP Cash",
                id = 8,
                type = AccountType.CASH,
                currentBalance = 100000, // £1,000.00
                currencyUnit = CurrencyUnit(Currency.getInstance("GBP")),
                equivalentCurrentBalance = (100000 * 1.28).roundToLong(),
            ),
            FullAccount(
                label = "Loan Account",
                id = 9,
                type = AccountType.LIABILITY,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = -1000000, // -$10,000.00 (loan debt)
            ),
            FullAccount(
                label = "Petty Cash",
                id = 10,
                type = AccountType.CASH,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 10000,
            ),
            FullAccount(
                label = "Emergency Savings Account",
                id = 11,
                type = AccountType.BANK,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 875000,
            ),
            FullAccount(
                label = "Long Term Investment Portfolio",
                id = 12,
                type = AccountType.ASSET,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 3250000,
            )
        ),
        showHiddenState = remember { mutableStateOf(false) },
        showZeroState = remember { mutableStateOf(false) },
        showChartState = remember { mutableStateOf(true) },
    )
}

