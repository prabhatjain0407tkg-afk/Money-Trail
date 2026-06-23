package com.expensetracker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.expensetracker.sms.categorizer.Categorizer
import com.expensetracker.sms.categorizer.SubCategoryRules
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.expensetracker.sms.model.Category
import com.expensetracker.sms.model.ParsedSms
import com.expensetracker.sms.model.PaymentMethod
import com.expensetracker.sms.model.SubCategory
import com.expensetracker.sms.model.TxType
import com.expensetracker.sms.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ─── 3-colour system ─────────────────────────────────────────────────────────
// Every pixel is a variation of Void, Lumen, or Aureate.
private val Void    = Color(0xFF06060C)  // near-black with blue undertone
private val Lumen   = Color(0xFFEEEAE0)  // warm off-white
private val Aureate = Color(0xFFD4A85C)  // deep warm gold

// Semantic only — debit/credit amounts and the two overview stripes
private val DebitColor   = Color(0xFFBF5B4C)  // muted brick red
private val CreditColor  = Color(0xFF4B9E74)  // muted pine green

// ─── Theme system ─────────────────────────────────────────────────────────────
enum class AppTheme(val label: String, val icon: String) {
    BLACK("Black",  "⬛"),
    GREY("Grey",    "🔘"),
    WHITE("White",  "⬜"),
    BEIGE("Beige",  "🟤"),
    NAVY("Navy",    "🔷")
}

data class AppColors(
    val appBg: Color,
    val cardBg: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentGold: Color
)

fun appColors(theme: AppTheme): AppColors = when (theme) {
    AppTheme.BLACK -> AppColors(
        appBg         = Color(0xFF06060C),
        cardBg        = Color(0xFF0F0F16),
        cardSurface   = Color(0xFF18181F),
        cardBorder    = Color(0xFF24242E),
        primaryText   = Color(0xFFEEEAE0),
        secondaryText = Color(0xFF6C6A7A),
        accentGold    = Color(0xFFD4A85C)
    )
    AppTheme.GREY -> AppColors(
        appBg         = Color(0xFF1E1E26),
        cardBg        = Color(0xFF272730),
        cardSurface   = Color(0xFF30303C),
        cardBorder    = Color(0xFF42424E),
        primaryText   = Color(0xFFEEEAE0),
        secondaryText = Color(0xFF9090A4),
        accentGold    = Color(0xFFD4A85C)
    )
    AppTheme.WHITE -> AppColors(
        appBg         = Color(0xFFF4F2EC),
        cardBg        = Color(0xFFFFFFFF),
        cardSurface   = Color(0xFFEBE9E3),
        cardBorder    = Color(0xFFD5D3CB),
        primaryText   = Color(0xFF0D0D14),
        secondaryText = Color(0xFF7A7870),
        accentGold    = Color(0xFFB07820)
    )
    // Warm beige canvas, cards in cream, green as the accent touch
    AppTheme.BEIGE -> AppColors(
        appBg         = Color(0xFFEDE0CE),  // warm sand beige
        cardBg        = Color(0xFFF7EFE0),  // creamy card surface
        cardSurface   = Color(0xFFE8D9C4),  // slightly darker inset
        cardBorder    = Color(0xFFCFBFA6),  // muted tan border
        primaryText   = Color(0xFF2A1F10),  // deep warm brown text
        secondaryText = Color(0xFF7A5F42),  // medium brown secondary
        accentGold    = Color(0xFF3D7A52)   // forest green accent (the small green)
    )
    // Deep navy glassy with brown warmth as the accent touch
    AppTheme.NAVY -> AppColors(
        appBg         = Color(0xFF0C1829),  // deep navy base
        cardBg        = Color(0xFF112238),  // glassy navy card
        cardSurface   = Color(0xFF162D47),  // lighter navy inset
        cardBorder    = Color(0xFF2A4A6B),  // blue-tinted border glow
        primaryText   = Color(0xFFD6E8F5),  // cool pale blue-white text
        secondaryText = Color(0xFF6A90B0),  // muted steel blue secondary
        accentGold    = Color(0xFF9B6B3A)   // warm cognac brown accent (the small brown)
    )
}

val LocalAppColors = compositionLocalOf { appColors(AppTheme.BLACK) }

// ─── Amount masking ───────────────────────────────────────────────────────────
val LocalShowAmounts = compositionLocalOf { true }

// ─── Custom categories ────────────────────────────────────────────────────────
val LocalCustomCategories          = compositionLocalOf<List<SubCategory>> { emptyList() }
val LocalOnCustomCategoryChanged   = compositionLocalOf<() -> Unit> { {} }
val LocalVoidTransaction            = compositionLocalOf<(String) -> Unit> { {} }
val LocalRestoreTransaction         = compositionLocalOf<(String) -> Unit> { {} }

fun fmtAmt(amount: Double, show: Boolean, prefix: String = "₹"): String =
    if (show) "$prefix%.0f".format(amount) else "$prefix ••••"

// ─── Navigation ───────────────────────────────────────────────────────────────
private sealed class Screen {
    data class CategoryDetail(
        val category: Category,
        val subCatFilter: String?  = null,
        val titleOverride: String? = null,
        val iconOverride: String?  = null
    ) : Screen()
    data class PaymentMethodDetail(val method: PaymentMethod) : Screen()
    object IncomingOverview : Screen()
    object SpentOverview    : Screen()
    object CreditCards      : Screen()
    data class CreditCardDetail(val cardId: String) : Screen()
    object Profile          : Screen()
    object ReferFriend      : Screen()
    object About            : Screen()
    object Search           : Screen()
    object Duplicates       : Screen()
}

// Derived from paymentMethod + bank + accountTail — no data-model change needed
val ParsedWithCategory.creditCardId: String?
    get() = if (paymentMethod == PaymentMethod.CREDIT_CARD && sms.accountTail != null)
                "${sms.bank} XX${sms.accountTail}" else null

// ─── Period ───────────────────────────────────────────────────────────────────
enum class Period(val label: String) {
    TODAY("Today"), WEEK("This Week"), MONTH("This Month"), ALL("All Time"), CUSTOM("Custom")
}

// ─── Category detail — sort / group ──────────────────────────────────────────
enum class TxSort(val label: String) {
    DATE_NEW("↓ Date"), DATE_OLD("↑ Date"), AMOUNT_HIGH("↓ Amount"), AMOUNT_LOW("↑ Amount")
}
enum class TxGroup(val label: String) {
    NONE("None"), PAYEE("By Payee"), PAYMENT("By Payment")
}
data class TxGroupSection(
    val key: String,
    val icon: String,
    val items: List<ParsedWithCategory>,
    val total: Double
)

fun periodStartMs(period: Period): Long {
    val cal = Calendar.getInstance()
    when (period) {
        Period.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        }
        Period.WEEK  -> {
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        }
        Period.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        }
        Period.ALL, Period.CUSTOM -> return 0L  // CUSTOM handled separately via explicit range
    }
    return cal.timeInMillis
}

// ─── Data model ───────────────────────────────────────────────────────────────
data class ParsedWithCategory(
    val sms: ParsedSms,
    val category: Category,
    val subCategory: SubCategory,
    val paymentMethod: PaymentMethod,
    val txKey: String,
    val isManualOverride: Boolean,
    val date: Long,
    val isVoided: Boolean = false,
    val upiId: String? = null,
    val hasOneTimeOverride: Boolean = false
)

// ─── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = LocalAppColors.current.appBg, surface = LocalAppColors.current.cardBg,
                    primary = LocalAppColors.current.accentGold, onBackground = LocalAppColors.current.primaryText, onSurface = LocalAppColors.current.primaryText
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = LocalAppColors.current.appBg) {
                    ExpenseTrackerApp()
                }
            }
        }
    }
}

// ─── Root ─────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp() {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    // Raw parsed SMS — no categorization; re-categorization happens in composition
    var rawParsed by remember { mutableStateOf<List<Pair<ParsedSms, Long>>>(emptyList()) }

    // User overrides — reloaded from SharedPreferences on every change
    var merchantOverrides     by remember { mutableStateOf(UserPrefsStore.loadMerchants(context)) }
    var keywordOverrides      by remember { mutableStateOf(UserPrefsStore.loadKeywords(context)) }
    var iconTagOverrides      by remember { mutableStateOf(UserPrefsStore.loadSubCategories(context)) }
    var oneTimeOverrides      by remember { mutableStateOf(UserPrefsStore.loadOneTime(context)) }
    var upiOverrides          by remember { mutableStateOf(UserPrefsStore.loadUpiRules(context)) }
    var showAmounts           by remember { mutableStateOf(true) }
    var appTheme              by remember { mutableStateOf(UserPrefsStore.loadTheme(context)) }

    var customCategories         by remember { mutableStateOf(UserPrefsStore.loadCustomCategories(context)) }
    var voidedTransactions       by remember { mutableStateOf(UserPrefsStore.loadVoidedTransactions(context)) }
    var aiEnabled    by remember { mutableStateOf(UserPrefsStore.isAiEnabled(context)) }
    var aiApiKey     by remember { mutableStateOf(UserPrefsStore.loadApiKey(context)) }
    var payeeNames   by remember { mutableStateOf(UserPrefsStore.loadPayeeNames(context)) }
    var txNotes      by remember { mutableStateOf(UserPrefsStore.loadNotes(context)) }
    var budgets      by remember { mutableStateOf(UserPrefsStore.loadBudgets(context)) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var isLoading          by remember { mutableStateOf(false) }
    var lastUpdatedMs      by remember { mutableStateOf(0L) }
    var hasNotifPermission by remember { mutableStateOf(SmsNotificationListener.isGranted(context)) }
    var selectedPeriod      by remember { mutableStateOf(Period.MONTH) }
    var customRangeStart    by remember { mutableStateOf<Long?>(null) }
    var customRangeEnd      by remember { mutableStateOf<Long?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val pagerState   = rememberPagerState(pageCount = { 2 })
    var screenStack  by remember { mutableStateOf<List<Screen>>(emptyList()) }
    val currentScreen = screenStack.lastOrNull()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Shared suspend action — reads inbox and stamps the sync time
    // Assignments go through Compose state delegates so any coroutine calling
    // this always writes to the live state, no stale-capture issues.
    suspend fun doRefresh() {
        rawParsed     = withContext(Dispatchers.IO) { readAndParseInbox(context) }
        lastUpdatedMs = System.currentTimeMillis()
    }

    // ── Initial load + ContentObserver + 15-min periodic refresh ──────────────
    // ContentObserver fires immediately when any new SMS lands in the inbox;
    // the periodic loop is a safety net for long foreground sessions.
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect

        isLoading = true
        doRefresh()
        isLoading = false

        val handler  = Handler(Looper.getMainLooper())
        var debounced = false
        val smsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (!debounced) {
                    debounced = true
                    // 2-second debounce so a burst of arriving SMS triggers one read
                    handler.postDelayed({
                        debounced = false
                        coroutineScope.launch { doRefresh() }
                    }, 2_000)
                }
            }
        }
        context.contentResolver.registerContentObserver(
            Uri.parse("content://sms/inbox"), true, smsObserver
        )

        try {
            while (true) {
                delay(15 * 60 * 1_000L)   // 15-minute periodic refresh
                doRefresh()
            }
        } finally {
            context.contentResolver.unregisterContentObserver(smsObserver)
        }
    }

    // ── Real-time refresh when NotificationListener captures a new RCS message ──
    DisposableEffect(Unit) {
        val notifPrefs = context.getSharedPreferences(
            SmsNotificationListener.PREFS_NAME, Context.MODE_PRIVATE
        )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SmsNotificationListener.KEY_MESSAGES) {
                coroutineScope.launch { doRefresh() }
            }
        }
        notifPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { notifPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // ── WorkManager: background 15-min check when app is closed ───────────────
    LaunchedEffect(Unit) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "expense_bg_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    // ── On-resume: reload if SmsReceiver or WorkManager flagged new data ───────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = SmsNotificationListener.isGranted(context)
                if (hasPermission) {
                    val lastBgSync = context
                        .getSharedPreferences("sync_state", Context.MODE_PRIVATE)
                        .getLong("last_bg_sync", 0)
                    if (lastBgSync > lastUpdatedMs) {
                        coroutineScope.launch { doRefresh() }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Derived state ─────────────────────────────────────────────────────────
    val allTransactions = remember(rawParsed, merchantOverrides, keywordOverrides, iconTagOverrides, oneTimeOverrides, upiOverrides, voidedTransactions) {
        val categorizer = Categorizer(merchantOverrides, keywordOverrides)
        fun resolveIconTag(iconEmoji: String?, cat: Category): SubCategory =
            SubCategoryRules.resolveIconTag(iconEmoji, cat, categoryIcon(cat))
        rawParsed.map { (sms, date) ->
            val txKey       = "${date}_${sms.rawText.hashCode()}"
            val pm          = detectPaymentMethod(sms.rawText)
            val hasOneTime  = oneTimeOverrides.containsKey(txKey)
            val detectedUpi = extractUpiId(sms.rawText)
            val hasUpiRule  = detectedUpi != null && upiOverrides.containsKey(detectedUpi)
            val cat = when {
                hasOneTime -> oneTimeOverrides[txKey]!!.first
                hasUpiRule -> upiOverrides[detectedUpi!!]!!.first
                else       -> categorizer.categorize(sms)
            }
            // Icon tag priority: one-time > UPI rule > merchant rule
            val iconEmoji = when {
                hasOneTime  -> oneTimeOverrides[txKey]!!.second
                hasUpiRule  -> upiOverrides[detectedUpi!!]!!.second
                else        -> sms.merchant?.lowercase()?.trim()?.let { iconTagOverrides[it] }
            }
            val subCat = resolveIconTag(iconEmoji, cat)
            ParsedWithCategory(sms, cat, subCat, pm, txKey, hasOneTime || hasUpiRule, date,
                isVoided = txKey in voidedTransactions,
                upiId = detectedUpi,
                hasOneTimeOverride = hasOneTime)
        }
    }

    // ── Recurring transaction detection ───────────────────────────────────────
    // A source (UPI ID or merchant) is "recurring" if it appears in 2+ distinct calendar months
    val recurringKeys: Set<String> = remember(allTransactions) {
        val cal = Calendar.getInstance()
        val sourceMonths = mutableMapOf<String, MutableSet<String>>()
        allTransactions.filter { it.sms.type == TxType.DEBIT }.forEach { tx ->
            val key = tx.upiId?.lowercase() ?: tx.sms.merchant?.lowercase()?.trim() ?: return@forEach
            cal.timeInMillis = tx.date
            val monthKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
            sourceMonths.getOrPut(key) { mutableSetOf() }.add(monthKey)
        }
        sourceMonths.filter { it.value.size >= 2 }.keys.toSet()
    }

    // ── Duplicate transaction detection ───────────────────────────────────────
    // Same amount + same bank + within 10 minutes = likely duplicate SMS
    val duplicateTxKeys: Set<String> = remember(allTransactions) {
        val dupes = mutableSetOf<String>()
        val sorted = allTransactions.sortedBy { it.date }
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]; val b = sorted[j]
                if (b.date - a.date > 10 * 60 * 1000L) break
                if (a.sms.amount == b.sms.amount && a.sms.bank == b.sms.bank && a.sms.type == b.sms.type) {
                    dupes += a.txKey; dupes += b.txKey
                }
            }
        }
        dupes
    }

    val transactions = remember(allTransactions, selectedPeriod, customRangeStart, customRangeEnd) {
        when (selectedPeriod) {
            Period.ALL    -> allTransactions
            Period.CUSTOM -> {
                val s = customRangeStart ?: 0L
                // +86399999 = 23:59:59.999 on the end day so the full end day is included
                val e = (customRangeEnd ?: Long.MAX_VALUE / 2) + 86_399_999L
                allTransactions.filter { it.date in s..e }
            }
            else -> {
                val start = periodStartMs(selectedPeriod)
                allTransactions.filter { it.date >= start }
            }
        }
    }

    // ── User category + icon-tag override handler ─────────────────────────────
    val onCategoryChange = { item: ParsedWithCategory, newCat: Category, newIconTag: SubCategory?, keyword: String?, isOneTime: Boolean, upiId: String? ->
        when {
            isOneTime -> {
                UserPrefsStore.saveOneTime(context, item.txKey, newCat, newIconTag?.icon)
                oneTimeOverrides = UserPrefsStore.loadOneTime(context)
            }
            upiId != null -> {
                UserPrefsStore.saveUpiRule(context, upiId, newCat, newIconTag?.icon)
                upiOverrides = UserPrefsStore.loadUpiRules(context)
            }
            else -> {
                val key = item.sms.merchant ?: keyword?.trim()?.ifEmpty { null }
                if (key != null) {
                    val isKeyword = item.sms.merchant == null
                    if (isKeyword) {
                        UserPrefsStore.saveKeyword(context, key, newCat)
                        keywordOverrides = UserPrefsStore.loadKeywords(context)
                    } else {
                        UserPrefsStore.saveMerchant(context, key, newCat)
                        merchantOverrides = UserPrefsStore.loadMerchants(context)
                    }
                    // Save or clear icon tag at merchant level
                    if (newIconTag != null && newIconTag.icon != categoryIcon(newCat)) {
                        UserPrefsStore.saveSubCategory(context, key, newIconTag.icon)
                    } else {
                        UserPrefsStore.removeSubCategory(context, key)
                    }
                    iconTagOverrides = UserPrefsStore.loadSubCategories(context)
                }
            }
        }
    }

    // ── Profile state ──────────────────────────────────────────────────────────
    var isProfileSetup  by remember { mutableStateOf(UserPrefsStore.isProfileSetup(context)) }
    var profileName     by remember { mutableStateOf(UserPrefsStore.loadProfile(context).first) }
    var profilePhone    by remember { mutableStateOf(UserPrefsStore.loadProfile(context).second) }
    var profileAvatarPath by remember { mutableStateOf(UserPrefsStore.loadProfile(context).third) }

    // Show profile setup screen on first install
    if (!isProfileSetup) {
        ProfileSetupScreen { name, phone, avatarPath ->
            UserPrefsStore.saveProfile(context, name, phone, avatarPath)
            profileName = name; profilePhone = phone; profileAvatarPath = avatarPath
            isProfileSetup = true
        }
        return
    }

    // ── Drawer ────────────────────────────────────────────────────────────────
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Open || screenStack.isNotEmpty() || pagerState.currentPage != 0) {
        when {
            drawerState.currentValue == DrawerValue.Open -> coroutineScope.launch { drawerState.close() }
            screenStack.isNotEmpty() -> screenStack = screenStack.dropLast(1)
            else -> coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors(appTheme),
        LocalShowAmounts provides showAmounts,
        LocalCustomCategories provides customCategories,
        LocalOnCustomCategoryChanged provides { customCategories = UserPrefsStore.loadCustomCategories(context) },
        LocalVoidTransaction provides { txKey ->
            UserPrefsStore.voidTransaction(context, txKey)
            voidedTransactions = UserPrefsStore.loadVoidedTransactions(context)
        },
        LocalRestoreTransaction provides { txKey ->
            UserPrefsStore.restoreTransaction(context, txKey)
            voidedTransactions = UserPrefsStore.loadVoidedTransactions(context)
        }
    ) {
    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = currentScreen == null,
        drawerContent   = {
            ModalDrawerSheet(
                drawerContainerColor = LocalAppColors.current.cardBg,
                modifier             = Modifier.fillMaxHeight().width(300.dp)
            ) {
                ProfileDrawerContent(
                    name        = profileName,
                    phone       = profilePhone,
                    avatarPath  = profileAvatarPath,
                    aiEnabled   = aiEnabled,
                    hasApiKey   = aiApiKey != null,
                    appTheme    = appTheme,
                    onThemeChange = { t ->
                        appTheme = t
                        UserPrefsStore.saveTheme(context, t)
                    },
                    onAiToggled = { enabled ->
                        UserPrefsStore.setAiEnabled(context, enabled)
                        aiEnabled = enabled
                        if (enabled && aiApiKey == null) {
                            coroutineScope.launch { drawerState.close() }
                            screenStack = screenStack + Screen.Profile
                        }
                    },
                    onNavigate  = { screen ->
                        coroutineScope.launch { drawerState.close() }
                        screenStack = screenStack + screen
                    },
                    onClose     = { coroutineScope.launch { drawerState.close() } }
                )
            }
        }
    ) {
    when (val d = currentScreen) {
        is Screen.CategoryDetail -> CategoryDetailScreen(
            category         = d.category,
            transactions     = transactions.filter { it.category == d.category },
            allDebits        = transactions.filter { it.sms.type == TxType.DEBIT },
            allTransactions  = allTransactions,
            onBack           = { screenStack = screenStack.dropLast(1) },
            onCategoryChange = onCategoryChange,
            titleOverride    = d.titleOverride,
            iconOverride     = d.iconOverride,
            aiApiKey         = if (aiEnabled) aiApiKey else null,
            onAiSetup        = { screenStack = screenStack + Screen.Profile },
            payeeNames       = payeeNames,
            onPayeeNameSaved = { key, name ->
                UserPrefsStore.savePayeeName(context, key, name)
                payeeNames = UserPrefsStore.loadPayeeNames(context)
            },
            txNotes          = txNotes,
            onNoteSaved      = { key, note ->
                UserPrefsStore.saveNote(context, key, note)
                txNotes = UserPrefsStore.loadNotes(context)
            },
            budget           = budgets[d.category],
            onBudgetSaved    = { amt ->
                UserPrefsStore.saveBudget(context, d.category, amt)
                budgets = UserPrefsStore.loadBudgets(context)
            },
            recurringKeys    = recurringKeys,
            duplicateTxKeys  = duplicateTxKeys
        )
        is Screen.PaymentMethodDetail -> PaymentMethodDetailScreen(
            method           = d.method,
            transactions     = transactions.filter { it.paymentMethod == d.method },
            onBack           = { screenStack = screenStack.dropLast(1) },
            onCategoryChange = onCategoryChange
        )
        Screen.IncomingOverview -> IncomingScreen(
            transactions = transactions,
            onBack       = { screenStack = screenStack.dropLast(1) },
            onItemClick  = { cat, subCat ->
                screenStack = screenStack + Screen.CategoryDetail(
                    category      = cat,
                    subCatFilter  = subCat?.id,
                    titleOverride = subCat?.displayName,
                    iconOverride  = subCat?.icon
                )
            }
        )
        Screen.SpentOverview -> SpentScreen(
            transactions          = transactions,
            onBack                = { screenStack = screenStack.dropLast(1) },
            onCategoryClick       = { screenStack = screenStack + Screen.CategoryDetail(it) },
            onCustomCategoryClick = { sub ->
                screenStack = screenStack + Screen.CategoryDetail(
                    category      = Category.CUSTOM,
                    subCatFilter  = sub.id,
                    titleOverride = sub.displayName,
                    iconOverride  = sub.icon
                )
            }
        )
        Screen.CreditCards -> CreditCardsScreen(
            transactions  = transactions,
            onBack        = { screenStack = screenStack.dropLast(1) },
            onCardClick   = { cardId -> screenStack = screenStack + Screen.CreditCardDetail(cardId) }
        )
        is Screen.CreditCardDetail -> CreditCardDetailScreen(
            cardId        = d.cardId,
            transactions  = transactions.filter { it.creditCardId == d.cardId },
            onBack        = { screenStack = screenStack.dropLast(1) },
            onCategoryChange = onCategoryChange
        )
        Screen.Profile -> ProfileScreen(
            name        = profileName,
            phone       = profilePhone,
            avatarPath  = profileAvatarPath,
            aiEnabled   = aiEnabled,
            aiApiKey    = aiApiKey ?: "",
            onBack      = { screenStack = screenStack.dropLast(1) },
            onProfileUpdated = { n, p, a ->
                profileName = n; profilePhone = p; profileAvatarPath = a
            },
            onAiSettingsChanged = { enabled, key ->
                UserPrefsStore.setAiEnabled(context, enabled)
                UserPrefsStore.saveApiKey(context, key)
                aiEnabled = enabled
                aiApiKey  = UserPrefsStore.loadApiKey(context)
            }
        )
        Screen.ReferFriend -> ReferFriendScreen(
            onBack = { screenStack = screenStack.dropLast(1) }
        )
        Screen.About -> AboutScreen(
            onBack = { screenStack = screenStack.dropLast(1) }
        )
        Screen.Search -> SearchScreen(
            allTransactions  = allTransactions,
            payeeNames       = payeeNames,
            txNotes          = txNotes,
            onBack           = { screenStack = screenStack.dropLast(1) },
            onCategoryChange = onCategoryChange
        )
        Screen.Duplicates -> DuplicatesScreen(
            allTransactions  = allTransactions,
            duplicateTxKeys  = duplicateTxKeys,
            payeeNames       = payeeNames,
            txNotes          = txNotes,
            onBack           = { screenStack = screenStack.dropLast(1) },
            onCategoryChange = onCategoryChange
        )
        null -> Box(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Shared header + swipeable tab bar ─────────────────────────────
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Profile avatar — tapping opens the drawer
                    ProfileAvatar(
                        name       = profileName,
                        avatarPath = profileAvatarPath,
                        size       = 42.dp,
                        modifier   = Modifier.clickable { coroutineScope.launch { drawerState.open() } }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (profileName.isNotBlank()) {
                            Text(
                                "Hi, ${profileName.split(" ").first()} 👋",
                                color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                            )
                        }
                        Text("Money Trail", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("track your spend", color = LocalAppColors.current.primaryText.copy(alpha = 0.35f), fontSize = 10.sp, letterSpacing = 0.5.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Search
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(LocalAppColors.current.cardSurface)
                                .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(9.dp))
                                .clickable { screenStack = screenStack + Screen.Search },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔍", fontSize = 15.sp)
                        }
                        // Eyeball — hide/show amounts
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (showAmounts) LocalAppColors.current.cardSurface else LocalAppColors.current.accentGold.copy(alpha = 0.15f))
                                .border(1.dp, if (showAmounts) LocalAppColors.current.cardBorder else LocalAppColors.current.accentGold.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                                .clickable { showAmounts = !showAmounts },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (showAmounts) "👁" else "🙈", fontSize = 15.sp)
                        }
                        // Sync — yellow
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(LocalAppColors.current.accentGold)
                                .clickable { coroutineScope.launch { doRefresh() } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↻", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // Last sync timestamp — right-aligned under the buttons
                val syncLabel = remember(lastUpdatedMs) {
                    if (lastUpdatedMs == 0L) "Never synced"
                    else "synced " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(lastUpdatedMs))
                }
                Text(
                    syncLabel,
                    color = LocalAppColors.current.secondaryText,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = TextAlign.End
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Expenses", "Payment Methods").forEachIndexed { i, title ->
                        val sel = pagerState.currentPage == i
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { coroutineScope.launch { pagerState.animateScrollToPage(i) } }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                title,
                                color = if (sel) LocalAppColors.current.accentGold else LocalAppColors.current.secondaryText,
                                fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(6.dp))
                            if (sel)
                                Box(modifier = Modifier.width(52.dp).height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)).background(LocalAppColors.current.accentGold))
                            else
                                Spacer(Modifier.height(2.dp))
                        }
                    }
                }
                HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
            }
            // ── Pager: swipe left to open Payment Methods ─────────────────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                when (page) {
                    0 -> DashboardScreen(
                        hasPermission         = hasPermission,
                        isLoading             = isLoading,
                        transactions          = transactions,
                        selectedPeriod        = selectedPeriod,
                        lastUpdatedMs         = lastUpdatedMs,
                        onPeriodChange        = { selectedPeriod = it },
                        onShowDatePicker      = { showDateRangePicker = true },
                        customRangeStart      = customRangeStart,
                        customRangeEnd        = customRangeEnd,
                        onRequestPermission   = { launcher.launch(Manifest.permission.READ_SMS) },
                        onIncomingClick       = { screenStack = screenStack + Screen.IncomingOverview },
                        onSpentClick          = { screenStack = screenStack + Screen.SpentOverview },
                        onCreditCardClick     = { screenStack = screenStack + Screen.CreditCards },
                        onTransferClick       = { screenStack = screenStack + Screen.CategoryDetail(Category.TRANSFER) },
                        onCcPaymentClick      = { screenStack = screenStack + Screen.CategoryDetail(Category.CC_PAYMENT) },
                        onRefresh             = { coroutineScope.launch { doRefresh() } },
                        onToggleAmounts       = { showAmounts = !showAmounts },
                        hasNotifPermission    = hasNotifPermission,
                        onEnableNotifListener = {
                            context.startActivity(
                                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            )
                        },
                        profileName           = profileName,
                        profileAvatarPath     = profileAvatarPath,
                        onOpenDrawer          = { coroutineScope.launch { drawerState.open() } },
                        onSearchClick         = { screenStack = screenStack + Screen.Search },
                        onDuplicatesClick     = { screenStack = screenStack + Screen.Duplicates },
                        duplicateTxKeys       = duplicateTxKeys,
                        recurringKeys         = recurringKeys
                    )
                    1 -> PaymentMethodsScreen(
                        transactions     = transactions,
                        selectedPeriod   = selectedPeriod,
                        onPeriodChange   = { selectedPeriod = it },
                        onShowDatePicker = { showDateRangePicker = true },
                        customRangeStart = customRangeStart,
                        customRangeEnd   = customRangeEnd,
                        onMethodClick    = { screenStack = screenStack + Screen.PaymentMethodDetail(it) }
                    )
                }
            }
        } // Column
        // ── FAB: create a custom category ─────────────────────────────────
        FloatingActionButton(
            onClick          = { showCreateCategoryDialog = true },
            modifier         = Modifier.align(Alignment.BottomEnd).padding(16.dp, 0.dp, 16.dp, 28.dp),
            containerColor   = LocalAppColors.current.accentGold,
            contentColor     = Color.Black,
            shape            = RoundedCornerShape(16.dp)
        ) {
            Text("＋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        } // Box
    }
    } // ModalNavigationDrawer
    } // CompositionLocalProvider

    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateCategoryDialog = false },
            onCreate  = { newCat ->
                UserPrefsStore.saveCustomCategory(context, newCat)
                customCategories = UserPrefsStore.loadCustomCategories(context)
            }
        )
    }

    // ── Date range picker dialog ───────────────────────────────────────────────
    if (showDateRangePicker) {
        val dateRangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customRangeStart,
            initialSelectedEndDateMillis   = customRangeEnd
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = dateRangeState.selectedStartDateMillis
                        val e = dateRangeState.selectedEndDateMillis
                        if (s != null) {
                            customRangeStart = s
                            customRangeEnd   = e ?: s
                            selectedPeriod   = Period.CUSTOM
                        }
                        showDateRangePicker = false
                    },
                    enabled = dateRangeState.selectedStartDateMillis != null
                ) { Text("Apply", color = LocalAppColors.current.accentGold) }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel", color = LocalAppColors.current.secondaryText)
                }
            }
        ) {
            DateRangePicker(
                state    = dateRangeState,
                title    = { Text("Select date range", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.fillMaxWidth().height(500.dp)
            )
        }
    }
}

// ─── Profile image utilities ──────────────────────────────────────────────────
fun saveBitmapToInternalStorage(context: Context, uri: android.net.Uri): String? = runCatching {
    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val file = File(context.filesDir, "profile_avatar.jpg")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    file.absolutePath
}.getOrNull()

fun loadBitmapFromPath(path: String): Bitmap? = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()

// ─── Profile avatar ───────────────────────────────────────────────────────────
@Composable
fun ProfileAvatar(
    name: String,
    avatarPath: String?,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarPath) { avatarPath?.let { loadBitmapFromPath(it) } }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LocalAppColors.current.accentGold.copy(alpha = 0.2f))
            .border(1.5.dp, LocalAppColors.current.accentGold.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap           = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier         = Modifier.fillMaxSize(),
                contentScale     = ContentScale.Crop
            )
        } else {
            Text(
                text       = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color      = LocalAppColors.current.accentGold,
                fontSize   = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN 1 — Dashboard
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun DashboardScreen(
    hasPermission: Boolean,
    isLoading: Boolean,
    transactions: List<ParsedWithCategory>,
    selectedPeriod: Period,
    lastUpdatedMs: Long,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit,
    customRangeStart: Long?,
    customRangeEnd: Long?,
    onRequestPermission: () -> Unit,
    onIncomingClick: () -> Unit,
    onSpentClick: () -> Unit,
    onCreditCardClick: () -> Unit = {},
    onTransferClick: () -> Unit = {},
    onCcPaymentClick: () -> Unit = {},
    onRefresh: () -> Unit,
    onToggleAmounts: () -> Unit = {},
    hasNotifPermission: Boolean,
    onEnableNotifListener: () -> Unit,
    profileName: String = "",
    profileAvatarPath: String? = null,
    onOpenDrawer: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onDuplicatesClick: () -> Unit = {},
    duplicateTxKeys: Set<String> = emptySet(),
    recurringKeys: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    var rcsTipDismissed by remember { mutableStateOf(prefs.getBoolean("rcs_tip_dismissed", false)) }
    val isSamsungDefault = remember {
        try { Telephony.Sms.getDefaultSmsPackage(context) == "com.samsung.android.messaging" }
        catch (_: Exception) { false }
    }
    val showRcsTip = isSamsungDefault && !rcsTipDismissed

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        if (!hasPermission) { PermissionScreen(onRequestPermission); return }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LocalAppColors.current.accentGold)
                    Spacer(Modifier.height(12.dp))
                    Text("Reading your transactions…", color = LocalAppColors.current.secondaryText, fontSize = 13.sp)
                }
            }
            return
        }

        // Voided transactions are excluded from ALL calculations
        val debits   = transactions.filter { it.sms.type == TxType.DEBIT  && !it.isVoided }
        val credits  = transactions.filter { it.sms.type == TxType.CREDIT && !it.isVoided }
        // Self-transfers excluded — neither money spent nor income received
        val spent         = debits.filter  { it.category != Category.TRANSFER && it.category != Category.CC_PAYMENT }.sumOf { it.sms.amount }
        val received      = credits.filter { it.category != Category.TRANSFER && it.category != Category.CC_PAYMENT }.sumOf { it.sms.amount }
        val transferTotal = transactions.filter { it.category == Category.TRANSFER && !it.isVoided }.sumOf { it.sms.amount }
        val transferCount = transactions.count  { it.category == Category.TRANSFER && !it.isVoided }
        val ccBillTotal   = transactions.filter { it.category == Category.CC_PAYMENT && !it.isVoided }.sumOf { it.sms.amount }
        val ccBillCount   = transactions.count  { it.category == Category.CC_PAYMENT && !it.isVoided }

        // Credit card spending — subset of SPENT, for reference only (not double-counted)
        val ccTransactions = transactions.filter { it.paymentMethod == PaymentMethod.CREDIT_CARD
                && it.sms.type == TxType.DEBIT && it.category != Category.TRANSFER && !it.isVoided }
        val ccTotal     = ccTransactions.sumOf { it.sms.amount }
        val ccCardCount = ccTransactions.mapNotNull { it.creditCardId }.toSet().size
        // Top cards teaser (up to 4)
        val topCcCards  = ccTransactions
            .filter { it.creditCardId != null }
            .groupBy { it.creditCardId!! }
            .entries
            .sortedByDescending { (_, v) -> v.sumOf { it.sms.amount } }
            .take(4)
            .map { (cardId, txns) -> Triple("💳", cardId, txns.sumOf { it.sms.amount }) }

        // Top categories for the Spent teaser (up to 4)
        val topSpentCategories = debits
            .filter { it.category != Category.CUSTOM && it.category != Category.TRANSFER && it.category != Category.CC_PAYMENT }
            .groupBy { it.category }
            .entries
            .sortedByDescending { (_, v) -> v.sumOf { it.sms.amount } }
            .take(4)
            .map { (cat, txns) -> Triple(categoryIcon(cat), cat.displayName, txns.sumOf { it.sms.amount }) }

        // Top income sources for the Incoming teaser (up to 4 merchants/banks)
        val topIncomingItems = credits
            .groupBy { it.sms.merchant ?: it.sms.bank }
            .entries
            .sortedByDescending { (_, txns) -> txns.sumOf { it.sms.amount } }
            .take(4)
            .map { (label, txns) -> Triple("💰", label, txns.sumOf { it.sms.amount }) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Period dropdown + duplicate chip row
            val dupeCount = transactions.count { it.txKey in duplicateTxKeys }
            item {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── Period dropdown ──────────────────────────────────────
                    var periodMenuOpen by remember { mutableStateOf(false) }
                    val periodLabel = if (selectedPeriod == Period.CUSTOM && customRangeStart != null) {
                        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
                        "${fmt.format(Date(customRangeStart))} – ${fmt.format(Date(customRangeEnd ?: customRangeStart))}"
                    } else selectedPeriod.label

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(LocalAppColors.current.cardBg)
                                .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(20.dp))
                                .clickable { periodMenuOpen = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(periodLabel,
                                color = LocalAppColors.current.accentGold,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Text("▾", color = LocalAppColors.current.accentGold, fontSize = 10.sp)
                        }
                        DropdownMenu(
                            expanded = periodMenuOpen,
                            onDismissRequest = { periodMenuOpen = false },
                            containerColor = LocalAppColors.current.cardBg
                        ) {
                            Period.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(p.label,
                                            color = if (p == selectedPeriod) LocalAppColors.current.accentGold
                                                    else LocalAppColors.current.primaryText,
                                            fontWeight = if (p == selectedPeriod) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp)
                                    },
                                    onClick = {
                                        periodMenuOpen = false
                                        if (p == Period.CUSTOM) onShowDatePicker() else onPeriodChange(p)
                                    }
                                )
                            }
                        }
                    }

                    // ── Duplicate chip ───────────────────────────────────────
                    if (dupeCount > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DebitColor.copy(alpha = 0.10f))
                                .border(1.dp, DebitColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                .clickable { onDuplicatesClick() }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("$dupeCount duplicates",
                                color = DebitColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Notification Access banner — shown until user grants the permission
            if (!hasNotifPermission) {
                item {
                    NotificationAccessBanner(onEnable = onEnableNotifListener)
                    Spacer(Modifier.height(12.dp))
                }
            }

            // RCS tip card — shown once to Samsung Messages users
            if (showRcsTip) {
                item {
                    RcsTipCard(
                        onDismiss = {
                            prefs.edit().putBoolean("rcs_tip_dismissed", true).apply()
                            rcsTipDismissed = true
                        },
                        onOpenSamsungMessages = {
                            context.packageManager
                                .getLaunchIntentForPackage("com.samsung.android.messaging")
                                ?.let { context.startActivity(it) }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Full-width summary card
            item {
                SummaryCard(spent, received, transactions.size)
                Spacer(Modifier.height(12.dp))
            }

            // Two overview cards — Incoming and Spent side by side
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IncomingOverviewCard(
                        total    = received,
                        teasers  = topIncomingItems,
                        onClick  = onIncomingClick,
                        modifier = Modifier.weight(1f)
                    )
                    SpentOverviewCard(
                        total    = spent,
                        teasers  = topSpentCategories,
                        onClick  = onSpentClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Credit card overview — only shown when CC transactions exist
                if (ccTotal > 0) {
                    Spacer(Modifier.height(12.dp))
                    CreditCardOverviewCard(
                        total     = ccTotal,
                        cardCount = ccCardCount,
                        teasers   = topCcCards,
                        onClick   = onCreditCardClick
                    )
                }
                // CC bill payments row
                if (ccBillTotal > 0) {
                    Spacer(Modifier.height(10.dp))
                    CcBillPaymentRow(
                        total   = ccBillTotal,
                        count   = ccBillCount,
                        onClick = onCcPaymentClick
                    )
                }
                // Self-transfers row — only shown when transfers exist in this period
                if (transferTotal > 0) {
                    Spacer(Modifier.height(10.dp))
                    SelfTransferRow(
                        total    = transferTotal,
                        count    = transferCount,
                        onClick  = onTransferClick
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─── Credit card overview card ────────────────────────────────────────────────
@Composable
fun CreditCardOverviewCard(
    total: Double,
    cardCount: Int,
    teasers: List<Triple<String, String, Double>>,
    onClick: () -> Unit
) {
    val show = LocalShowAmounts.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Lumen.copy(alpha = 0.07f), Lumen.copy(alpha = 0.03f))))
            .border(0.5.dp, Lumen.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Aureate))
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("CREDIT CARD", color = Aureate, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(2.dp))
                Text("included in SPENT  •  $cardCount card${if (cardCount != 1) "s" else ""}",
                    color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                Text(fmtAmt(total, show), color = LocalAppColors.current.primaryText, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold)
            }
            Text("›", color = LocalAppColors.current.secondaryText, fontSize = 20.sp)
        }
    }
}

// ─── Credit cards screen (list of all cards) ──────────────────────────────────
@Composable
fun CreditCardsScreen(
    transactions: List<ParsedWithCategory>,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit
) {
    val show    = LocalShowAmounts.current
    val ccCards = transactions
        .filter { it.paymentMethod == PaymentMethod.CREDIT_CARD
                && it.sms.type == TxType.DEBIT && it.category != Category.TRANSFER }
        .groupBy { it.creditCardId ?: "Unknown Card" }
        .entries
        .sortedByDescending { (_, v) -> v.sumOf { it.sms.amount } }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                .clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Credit Cards", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("tap a card to see its transactions", color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
            }
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        // Disclaimer banner
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Aureate.copy(alpha = 0.08f))
            .border(0.5.dp, Aureate.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp)) {
            Text(
                "💳  These amounts are already included in SPENT. This view shows how your spending breaks down per card — no double counting.",
                color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 16.sp
            )
        }

        if (ccCards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💳", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No credit card transactions\nfound in this period.",
                        color = LocalAppColors.current.secondaryText, fontSize = 14.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center)
                }
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ccCards) { (cardId, txns) ->
                val total = txns.sumOf { it.sms.amount }
                val parts = cardId.split(" XX")
                val bank  = parts.firstOrNull() ?: cardId
                val tail  = if (parts.size == 2) parts[1] else "????"
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(
                            Lumen.copy(alpha = 0.07f), Lumen.copy(alpha = 0.03f))))
                        .border(0.5.dp, Lumen.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                        .clickable { onCardClick(cardId) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Card icon
                    Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                        .background(Aureate.copy(alpha = 0.12f))
                        .border(1.dp, Aureate.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center) {
                        Text("💳", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bank, color = LocalAppColors.current.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("•••• •••• •••• $tail", color = LocalAppColors.current.secondaryText, fontSize = 12.sp,
                            letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${txns.size} transaction${if (txns.size != 1) "s" else ""}",
                            color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmtAmt(total, show), color = LocalAppColors.current.primaryText, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text("›", color = LocalAppColors.current.secondaryText, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ─── Credit card detail screen (transactions on one card) ─────────────────────
@Composable
fun CreditCardDetailScreen(
    cardId: String,
    transactions: List<ParsedWithCategory>,
    onBack: () -> Unit,
    onCategoryChange: (ParsedWithCategory, Category, SubCategory?, String?, Boolean, String?) -> Unit
) {
    val context     = LocalContext.current
    val show        = LocalShowAmounts.current
    val total       = transactions.filter { it.sms.type == TxType.DEBIT }.sumOf { it.sms.amount }
    val parts       = cardId.split(" XX")
    val bank        = parts.firstOrNull() ?: cardId
    val tail        = if (parts.size == 2) parts[1] else "????"
    var settlements by remember { mutableStateOf(UserPrefsStore.loadSettlements(context)) }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                .clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bank, color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("•••• •••• •••• $tail  •  ${transactions.size} txns",
                    color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtAmt(total, show), color = LocalAppColors.current.primaryText, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
                Text("included in SPENT", color = LocalAppColors.current.secondaryText, fontSize = 9.sp)
            }
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions found.", color = LocalAppColors.current.secondaryText)
            }
            return
        }

        val debitRecoveries: Map<String, Double> = remember(settlements) {
            settlements.values.groupBy { it.first }
                .mapValues { (_, r) -> r.sumOf { it.third } }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(transactions.sortedByDescending { it.date }) { item ->
                TransactionCard(
                    item             = item,
                    allDebits        = transactions.filter { it.sms.type == TxType.DEBIT },
                    settlements      = settlements,
                    debitSettledAmount = debitRecoveries[item.txKey] ?: 0.0,
                    onCategoryChange = { cat, sub, kw, ot, upi ->
                        onCategoryChange(item, cat, sub, kw, ot, upi)
                    },
                    onSettlementChanged = { settlements = UserPrefsStore.loadSettlements(context) }
                )
            }
        }
    }
}

// ─── Self-transfer row ────────────────────────────────────────────────────────
@Composable
fun SelfTransferRow(total: Double, count: Int, onClick: () -> Unit) {
    val show = LocalShowAmounts.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LocalAppColors.current.cardSurface)
            .border(0.5.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⇄", color = LocalAppColors.current.secondaryText, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Self Transfers", color = LocalAppColors.current.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$count transaction${if (count > 1) "s" else ""}  •  not counted in totals",
                color = LocalAppColors.current.secondaryText.copy(alpha = 0.6f), fontSize = 10.sp)
        }
        Text(fmtAmt(total, show), color = LocalAppColors.current.secondaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("›", color = LocalAppColors.current.secondaryText, fontSize = 16.sp)
    }
}

@Composable
fun CcBillPaymentRow(total: Double, count: Int, onClick: () -> Unit) {
    val show = LocalShowAmounts.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Aureate.copy(alpha = 0.06f))
            .border(0.5.dp, Aureate.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("💳", fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("CC Bill Payments", color = Aureate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$count payment${if (count > 1) "s" else ""}  •  not counted in spent",
                color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
        }
        Text(fmtAmt(total, show), color = Aureate, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("›", color = Aureate, fontSize = 16.sp)
    }
}

// ─── Notification Access banner ──────────────────────────────────────────────
@Composable
fun NotificationAccessBanner(onEnable: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A1A0A))
            .border(1.dp, CreditColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔", fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Capture RCS & Chat transactions",
                    color = CreditColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Enable Notification Access so the app automatically captures bank messages " +
                "sent via RCS/Chat (like SBI Card) — these are invisible to SMS permission alone.",
                color = LocalAppColors.current.secondaryText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = CreditColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text(
                    "Enable Notification Access",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── RCS tip card ────────────────────────────────────────────────────────────
@Composable
fun RcsTipCard(onDismiss: () -> Unit, onOpenSamsungMessages: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1D1800))
            .border(1.dp, LocalAppColors.current.accentGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Improve credit card tracking",
                    color = LocalAppColors.current.accentGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    color = LocalAppColors.current.secondaryText,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "SBI Card and some banks send via RCS Chat, which SMS access can't read. " +
                "For complete transaction history, open Samsung Messages → Settings → Chat settings " +
                "and turn off Chat features.",
                color = LocalAppColors.current.secondaryText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Already done", color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                }
                Button(
                    onClick = onOpenSamsungMessages,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.accentGold),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Open Samsung Messages", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN 2 — Category Detail
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryDetailScreen(
    category: Category,
    transactions: List<ParsedWithCategory>,
    allDebits: List<ParsedWithCategory> = emptyList(),
    allTransactions: List<ParsedWithCategory> = emptyList(),
    onBack: () -> Unit,
    onCategoryChange: (ParsedWithCategory, Category, SubCategory?, String?, Boolean, String?) -> Unit,
    titleOverride: String? = null,
    iconOverride: String?  = null,
    aiApiKey: String?  = null,
    onAiSetup: () -> Unit = {},
    payeeNames: Map<String, String> = emptyMap(),
    onPayeeNameSaved: (String, String) -> Unit = { _, _ -> },
    txNotes: Map<String, String> = emptyMap(),
    onNoteSaved: (String, String) -> Unit = { _, _ -> },
    budget: Double? = null,
    onBudgetSaved: (Double) -> Unit = {},
    recurringKeys: Set<String> = emptySet(),
    duplicateTxKeys: Set<String> = emptySet()
) {
    val context    = LocalContext.current
    val debitTotal = transactions.filter { it.sms.type == TxType.DEBIT }.sumOf { it.sms.amount }

    var settlements by remember { mutableStateOf(UserPrefsStore.loadSettlements(context)) }
    val refreshSettlements: () -> Unit = { settlements = UserPrefsStore.loadSettlements(context) }

    val debitRecoveries: Map<String, Double> = remember(settlements) {
        settlements.values
            .groupBy { it.first }
            .mapValues { (_, records) -> records.sumOf { it.third } }
    }

    var sort  by remember { mutableStateOf(TxSort.DATE_NEW) }
    var group by remember { mutableStateOf(TxGroup.NONE) }

    val sorted = remember(transactions, sort) {
        when (sort) {
            TxSort.DATE_NEW    -> transactions.sortedByDescending { it.date }
            TxSort.DATE_OLD    -> transactions.sortedBy { it.date }
            TxSort.AMOUNT_HIGH -> transactions.sortedByDescending { it.sms.amount }
            TxSort.AMOUNT_LOW  -> transactions.sortedBy { it.sms.amount }
        }
    }

    val sections: List<TxGroupSection> = remember(sorted, group) {
        when (group) {
            TxGroup.NONE -> listOf(TxGroupSection("", "", sorted, 0.0))
            TxGroup.PAYEE -> sorted
                .groupBy { it.sms.merchant?.trim()?.ifBlank { null } ?: "Unknown" }
                .map { (name, items) ->
                    TxGroupSection(name, "👤", items,
                        items.filter { it.sms.type == TxType.DEBIT }.sumOf { it.sms.amount })
                }
                .sortedByDescending { it.total }
            TxGroup.PAYMENT -> sorted
                .groupBy { it.paymentMethod }
                .map { (method, items) ->
                    TxGroupSection(method.displayName, method.icon, items,
                        items.filter { it.sms.type == TxType.DEBIT }.sumOf { it.sms.amount })
                }
                .sortedByDescending { it.total }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.cardBg)
                    .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp) }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(iconOverride ?: categoryIcon(category), fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(titleOverride ?: category.displayName, color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${transactions.size} transactions  •  ${fmtAmt(debitTotal, LocalShowAmounts.current)} spent",
                    color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        // ── Budget bar ────────────────────────────────────────────────────────
        var showBudgetDialog by remember { mutableStateOf(false) }
        var budgetInput      by remember(budget) { mutableStateOf(budget?.toLong()?.toString() ?: "") }
        if (budget != null && budget > 0 && debitTotal > 0) {
            val pct       = (debitTotal / budget).toFloat().coerceIn(0f, 1f)
            val overBudget = debitTotal > budget
            val barColor  = when {
                overBudget     -> DebitColor
                pct >= 0.8f    -> Color(0xFFE6A817)
                else           -> CreditColor
            }
            Column(modifier = Modifier
                .background(LocalAppColors.current.cardSurface)
                .clickable { showBudgetDialog = true }
                .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (overBudget) "⚠️ Over budget by ₹${fmtAmt(debitTotal - budget, true)}"
                        else "Budget: ₹${fmtAmt(debitTotal, true)} of ₹${fmtAmt(budget, true)}",
                        color = barColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("${(pct * 100).toInt()}%  ✏️", color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                }
                Spacer(Modifier.height(5.dp))
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.cardBg)) {
                    Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(barColor))
                }
            }
            HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBudgetDialog = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("➕ Set monthly budget", color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
            }
            HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
        }
        if (showBudgetDialog) {
            AlertDialog(
                onDismissRequest = { showBudgetDialog = false },
                containerColor = LocalAppColors.current.cardSurface,
                title = { Text("Monthly Budget", color = LocalAppColors.current.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Set a monthly spending limit for ${titleOverride ?: category.displayName}.",
                            color = LocalAppColors.current.secondaryText, fontSize = 12.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { if (it.all(Char::isDigit)) budgetInput = it },
                            placeholder = { Text("e.g. 5000", color = LocalAppColors.current.secondaryText) },
                            prefix = { Text("₹ ", color = LocalAppColors.current.accentGold) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LocalAppColors.current.accentGold, unfocusedBorderColor = LocalAppColors.current.cardBorder,
                                focusedTextColor = LocalAppColors.current.primaryText, unfocusedTextColor = LocalAppColors.current.primaryText, cursorColor = LocalAppColors.current.accentGold
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        budgetInput.toDoubleOrNull()?.let { onBudgetSaved(it) }
                        showBudgetDialog = false
                    }) { Text("Save", color = LocalAppColors.current.accentGold, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (budget != null) { onBudgetSaved(0.0) } // clear
                        showBudgetDialog = false
                    }) { Text(if (budget != null) "Remove" else "Cancel", color = LocalAppColors.current.secondaryText) }
                }
            )
        }

        // ── Month-over-month comparison ───────────────────────────────────────
        val momComparison = remember(allTransactions, category) {
            val now        = Calendar.getInstance()
            val thisMonth  = now.get(Calendar.MONTH)
            val thisYear   = now.get(Calendar.YEAR)
            val lastMonth  = if (thisMonth == 0) 11 else thisMonth - 1
            val lastYear   = if (thisMonth == 0) thisYear - 1 else thisYear

            fun monthSpend(month: Int, year: Int): Double {
                val cal = Calendar.getInstance()
                return allTransactions.filter {
                    cal.timeInMillis = it.date
                    it.category == category &&
                    it.sms.type == TxType.DEBIT && !it.isVoided &&
                    cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
                }.sumOf { it.sms.amount }
            }
            val thisSpend = monthSpend(thisMonth, thisYear)
            val lastSpend = monthSpend(lastMonth, lastYear)
            if (lastSpend > 0) Triple(thisSpend, lastSpend, (thisSpend - lastSpend) / lastSpend * 100) else null
        }
        if (momComparison != null) {
            val (thisSpend, lastSpend, pctChange) = momComparison
            val lastMonthName = SimpleDateFormat("MMM", Locale.getDefault()).format(
                Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time
            )
            val arrow = if (pctChange >= 0) "▲" else "▼"
            val color = if (pctChange >= 0) DebitColor else CreditColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalAppColors.current.cardSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("vs $lastMonthName", color = LocalAppColors.current.secondaryText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(
                    "$arrow ${String.format("%.0f", if (pctChange < 0) -pctChange else pctChange)}%  (₹${fmtAmt(lastSpend, true)} last month)",
                    color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
        }

        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions in this period.", color = LocalAppColors.current.secondaryText)
            }
            return
        }

        // ── Sort + Group filter bar ───────────────────────────────────────────
        var sortExpanded  by remember { mutableStateOf(false) }
        var groupExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .background(LocalAppColors.current.appBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort dropdown
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(LocalAppColors.current.cardSurface)
                        .border(1.dp, if (sort != TxSort.DATE_NEW) LocalAppColors.current.accentGold else LocalAppColors.current.cardBorder, RoundedCornerShape(20.dp))
                        .clickable { sortExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Sort", color = LocalAppColors.current.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(sort.label, color = if (sort != TxSort.DATE_NEW) LocalAppColors.current.accentGold else LocalAppColors.current.primaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("▾", color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                    modifier = Modifier.background(LocalAppColors.current.cardSurface)
                ) {
                    TxSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(s.label,
                                    color = if (sort == s) LocalAppColors.current.accentGold else LocalAppColors.current.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = if (sort == s) FontWeight.Bold else FontWeight.Normal)
                            },
                            onClick = { sort = s; sortExpanded = false }
                        )
                    }
                }
            }
            // Group dropdown
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(LocalAppColors.current.cardSurface)
                        .border(1.dp, if (group != TxGroup.NONE) LocalAppColors.current.accentGold else LocalAppColors.current.cardBorder, RoundedCornerShape(20.dp))
                        .clickable { groupExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Group", color = LocalAppColors.current.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(group.label, color = if (group != TxGroup.NONE) LocalAppColors.current.accentGold else LocalAppColors.current.primaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("▾", color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                }
                DropdownMenu(
                    expanded = groupExpanded,
                    onDismissRequest = { groupExpanded = false },
                    modifier = Modifier.background(LocalAppColors.current.cardSurface)
                ) {
                    TxGroup.entries.forEach { g ->
                        DropdownMenuItem(
                            text = {
                                Text(g.label,
                                    color = if (group == g) LocalAppColors.current.accentGold else LocalAppColors.current.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = if (group == g) FontWeight.Bold else FontWeight.Normal)
                            },
                            onClick = { group = g; groupExpanded = false }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        // ── Transaction list ──────────────────────────────────────────────────
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            sections.forEach { section ->
                if (group != TxGroup.NONE) {
                    stickyHeader(key = "hdr_${section.key}") {
                        TxGroupHeader(
                            icon  = section.icon,
                            label = section.key,
                            count = section.items.size,
                            total = section.total
                        )
                    }
                }
                items(section.items, key = { it.txKey }) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        val payeeKey = item.upiId?.lowercase()
                            ?: item.sms.merchant?.lowercase()?.trim()
                            ?: item.txKey
                        TransactionCard(
                            item                     = item,
                            showSubcategoryAsPrimary = false,
                            allDebits                = allDebits,
                            settlements              = settlements,
                            debitSettledAmount       = debitRecoveries[item.txKey] ?: 0.0,
                            onSettlementChanged      = refreshSettlements,
                            aiApiKey                 = aiApiKey,
                            onAiSetup                = onAiSetup,
                            customPayeeName          = payeeNames[payeeKey],
                            onPayeeNameSaved         = { name -> onPayeeNameSaved(payeeKey, name) },
                            note                     = txNotes[item.txKey],
                            onNoteSaved              = { n -> onNoteSaved(item.txKey, n) },
                            isRecurring              = run {
                                val k = item.upiId?.lowercase() ?: item.sms.merchant?.lowercase()?.trim()
                                k != null && recurringKeys.contains(k)
                            },
                            isDuplicate              = item.txKey in duplicateTxKeys,
                            onCategoryChange         = { newCat, newSubCat, keyword, isOneTime, upiId ->
                                onCategoryChange(item, newCat, newSubCat, keyword, isOneTime, upiId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TxGroupHeader(icon: String, label: String, count: Int, total: Double) {
    val show = LocalShowAmounts.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalAppColors.current.appBg)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(LocalAppColors.current.cardBg),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 15.sp) }
        Spacer(Modifier.width(10.dp))
        Text(label, color = LocalAppColors.current.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            if (total > 0) {
                Text(fmtAmt(total, show), color = DebitColor,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text("$count txn${if (count != 1) "s" else ""}",
                color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
        }
    }
    HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) LocalAppColors.current.accentGold else LocalAppColors.current.cardBg)
            .border(1.dp, if (selected) LocalAppColors.current.accentGold else LocalAppColors.current.cardBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.Black else LocalAppColors.current.secondaryText,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SummaryCard(spent: Double, received: Double, count: Int) {
    val savings = received - spent
    val show    = LocalShowAmounts.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppColors.current.cardBg)
            .border(0.5.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SPENT
        Column(modifier = Modifier.weight(1f)) {
            Text("SPENT", color = DebitColor, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(3.dp))
            Text(fmtAmt(spent, show), color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        // NET (centre)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NET", color = LocalAppColors.current.secondaryText, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                if (show) "${if (savings >= 0) "+" else ""}₹%.0f".format(savings) else "₹ ••",
                color = if (savings >= 0) CreditColor else DebitColor,
                fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text("$count txns", color = LocalAppColors.current.secondaryText, fontSize = 9.sp)
        }
        // RECEIVED
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("RECEIVED", color = CreditColor, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(3.dp))
            Text(fmtAmt(received, show), color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MiniStat(label: String, value: String, color: Color) {
    Column {
        Text(label, color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Sync bar ─────────────────────────────────────────────────────────────────
@Composable
fun SyncBar(lastUpdatedMs: Long, onRefresh: () -> Unit) {
    val syncLabel = remember(lastUpdatedMs) {
        if (lastUpdatedMs == 0L) "Never synced"
        else SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(lastUpdatedMs))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Last Sync", color = LocalAppColors.current.secondaryText, fontSize = 11.sp, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(2.dp))
            Text(syncLabel, color = LocalAppColors.current.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(LocalAppColors.current.accentGold)
                .clickable { onRefresh() }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text("↻  Sync Now", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROFILE — Setup / Drawer / Screens
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ProfileSetupScreen(onComplete: (name: String, phone: String, avatarPath: String?) -> Unit) {
    val context    = LocalContext.current
    var name       by remember { mutableStateOf("") }
    var phone      by remember { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { avatarPath = saveBitmapToInternalStorage(context, it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))

        Text("💰", fontSize = 52.sp)
        Spacer(Modifier.height(8.dp))
        Text("Money Trail", color = LocalAppColors.current.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Smart spend tracking", color = LocalAppColors.current.secondaryText, fontSize = 13.sp)

        Spacer(Modifier.height(40.dp))

        // Avatar picker
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape)
                .background(LocalAppColors.current.cardSurface)
                .border(2.dp, LocalAppColors.current.accentGold.copy(alpha = 0.5f), CircleShape)
                .clickable { imagePicker.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            val bmp = remember(avatarPath) { avatarPath?.let { loadBitmapFromPath(it) } }
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👤", fontSize = 32.sp)
                    Text("Photo", color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                }
            }
        }
        Text("Optional", color = LocalAppColors.current.secondaryText.copy(alpha = 0.6f), fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp))

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value       = name,
            onValueChange = { name = it },
            label       = { Text("Your Name") },
            modifier    = Modifier.fillMaxWidth(),
            singleLine  = true,
            colors      = profileFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value       = phone,
            onValueChange = { if (it.length <= 10) phone = it.filter(Char::isDigit) },
            label       = { Text("Mobile Number") },
            modifier    = Modifier.fillMaxWidth(),
            singleLine  = true,
            prefix      = { Text("+91  ", color = LocalAppColors.current.secondaryText) },
            colors      = profileFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
        )

        Spacer(Modifier.height(36.dp))

        val ready = name.isNotBlank() && phone.length == 10
        Button(
            onClick  = { onComplete(name.trim(), phone.trim(), avatarPath) },
            enabled  = ready,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = LocalAppColors.current.accentGold,
                disabledContainerColor = LocalAppColors.current.cardSurface
            )
        ) {
            Text("Get Started  →",
                color      = if (ready) Color.Black else LocalAppColors.current.secondaryText,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor        = LocalAppColors.current.accentGold,
    unfocusedBorderColor      = LocalAppColors.current.cardBorder,
    focusedLabelColor         = LocalAppColors.current.accentGold,
    unfocusedLabelColor       = LocalAppColors.current.secondaryText,
    focusedTextColor          = LocalAppColors.current.primaryText,
    unfocusedTextColor        = LocalAppColors.current.primaryText,
    cursorColor               = LocalAppColors.current.accentGold,
    focusedContainerColor     = Color.Transparent,
    unfocusedContainerColor   = Color.Transparent
)

// ─── Profile drawer ───────────────────────────────────────────────────────────
@Composable
private fun ProfileDrawerContent(
    name: String,
    phone: String,
    avatarPath: String?,
    aiEnabled: Boolean = false,
    hasApiKey: Boolean = false,
    appTheme: AppTheme = AppTheme.BLACK,
    onThemeChange: (AppTheme) -> Unit = {},
    onAiToggled: (Boolean) -> Unit = {},
    onNavigate: (Screen) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).background(LocalAppColors.current.cardBg)) {
        // Profile header
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(LocalAppColors.current.cardSurface)
                .padding(24.dp)
        ) {
            Column {
                ProfileAvatar(name = name, avatarPath = avatarPath, size = 68.dp)
                Spacer(Modifier.height(14.dp))
                Text(name, color = LocalAppColors.current.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("+91 $phone", color = LocalAppColors.current.secondaryText, fontSize = 13.sp)
            }
        }

        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        // ── Theme selector ────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Text("THEME", color = LocalAppColors.current.secondaryText, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTheme.entries.forEach { theme ->
                    val selected = theme == appTheme
                    val colors = appColors(theme)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardBg)
                            .then(
                                if (selected)
                                    Modifier.border(2.dp, LocalAppColors.current.accentGold, RoundedCornerShape(12.dp))
                                else
                                    Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                            )
                            .clickable { onThemeChange(theme) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(theme.icon, fontSize = 18.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(theme.label,
                                color = if (selected) LocalAppColors.current.accentGold else colors.secondaryText,
                                fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))

        listOf(
            Triple("👤", "Profile",         Screen.Profile as Screen),
            Triple("🎁", "Refer a Friend",  Screen.ReferFriend as Screen),
            Triple("ℹ️", "About Us",        Screen.About as Screen)
        ).forEach { (icon, label, screen) ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onNavigate(screen) }
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.width(16.dp))
                Text(label, color = LocalAppColors.current.primaryText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("›", color = LocalAppColors.current.secondaryText, fontSize = 18.sp)
            }
            HorizontalDivider(color = LocalAppColors.current.cardBorder.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 22.dp))
        }

        // ── AI Suggestions toggle ──────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨", fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Suggestions", color = LocalAppColors.current.primaryText, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium)
                    Text(
                        if (aiEnabled && hasApiKey) "Active • Gemini 2.0 Flash"
                        else if (aiEnabled) "Tap Profile to add API key"
                        else "Off",
                        color = if (aiEnabled && hasApiKey) CreditColor else LocalAppColors.current.secondaryText,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked  = aiEnabled,
                    onCheckedChange = onAiToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = LocalAppColors.current.appBg,
                        checkedTrackColor   = LocalAppColors.current.accentGold,
                        uncheckedThumbColor = LocalAppColors.current.secondaryText,
                        uncheckedTrackColor = LocalAppColors.current.cardSurface
                    )
                )
            }

            // Privacy assurance — always visible when the toggle is shown
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.cardSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("🔒", fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Don't worry — we never see your data. Only the SMS text with the amount masked is sent to Google Gemini using your own personal API key.",
                    color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
        }
    }
}

// ─── Profile screen ───────────────────────────────────────────────────────────
@Composable
fun ProfileScreen(
    name: String,
    phone: String,
    avatarPath: String?,
    aiEnabled: Boolean = false,
    aiApiKey: String   = "",
    onBack: () -> Unit,
    onProfileUpdated: (String, String, String?) -> Unit,
    onAiSettingsChanged: (Boolean, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var editing         by remember { mutableStateOf(false) }
    var editName        by remember(name)       { mutableStateOf(name) }
    var editPhone       by remember(phone)      { mutableStateOf(phone) }
    var editAvatarPath  by remember(avatarPath) { mutableStateOf(avatarPath) }
    var localAiEnabled  by remember(aiEnabled)  { mutableStateOf(aiEnabled) }
    var localApiKey     by remember(aiApiKey)   { mutableStateOf(aiApiKey) }
    var apiKeyVisible   by remember { mutableStateOf(false) }
    var showApiKeyInput by remember { mutableStateOf(aiApiKey.isNotBlank()) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { editAvatarPath = saveBitmapToInternalStorage(context, it) }
    }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                .clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Text("Profile", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (!editing) {
                TextButton(onClick = { editing = true }) {
                    Text("Edit", color = LocalAppColors.current.accentGold, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Avatar
            val currentAvatar = if (editing) editAvatarPath else avatarPath
            val currentName   = if (editing) editName else name
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape)
                    .then(if (editing) Modifier.clickable { imagePicker.launch("image/*") } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                ProfileAvatar(name = currentName, avatarPath = currentAvatar, size = 100.dp)
                if (editing) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center) {
                        Text("✏️", fontSize = 26.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            if (editing) {
                OutlinedTextField(value = editName, onValueChange = { editName = it },
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, colors = profileFieldColors())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = editPhone,
                    onValueChange = { if (it.length <= 10) editPhone = it.filter(Char::isDigit) },
                    label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, prefix = { Text("+91  ", color = LocalAppColors.current.secondaryText) },
                    colors = profileFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editing = false },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.cardBorder)) {
                        Text("Cancel", color = LocalAppColors.current.secondaryText)
                    }
                    Button(
                        onClick = {
                            UserPrefsStore.saveProfile(context, editName.trim(), editPhone.trim(), editAvatarPath)
                            onProfileUpdated(editName.trim(), editPhone.trim(), editAvatarPath)
                            editing = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.accentGold),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Save", color = Color.Black, fontWeight = FontWeight.SemiBold) }
                }
            } else {
                // View mode
                listOf("Name" to name, "Mobile" to "+91 $phone").forEach { (label, value) ->
                    Column(modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LocalAppColors.current.cardBg)
                        .padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(label, color = LocalAppColors.current.secondaryText, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(value, color = LocalAppColors.current.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── AI Suggestions section ────────────────────────────────────────
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(20.dp))

            Text("AI SUGGESTIONS", color = LocalAppColors.current.secondaryText, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(12.dp))

            // Enable / disable toggle
            Column(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(LocalAppColors.current.cardBg)
                .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Category Suggestions", color = LocalAppColors.current.primaryText,
                            fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Powered by Google Gemini (free)",
                            color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                    }
                    Switch(
                        checked  = localAiEnabled,
                        onCheckedChange = { enabled ->
                            localAiEnabled  = enabled
                            showApiKeyInput = enabled
                            onAiSettingsChanged(enabled, localApiKey)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = LocalAppColors.current.appBg,
                            checkedTrackColor  = LocalAppColors.current.accentGold,
                            uncheckedThumbColor = LocalAppColors.current.secondaryText,
                            uncheckedTrackColor = LocalAppColors.current.cardSurface
                        )
                    )
                }
                if (localAiEnabled && localApiKey.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("⚠ Add your API key below to start using AI suggestions",
                        color = DebitColor.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }

            // Privacy assurance — always visible in AI section
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LocalAppColors.current.cardBg)
                    .border(0.5.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("🔒", fontSize = 14.sp, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Your data is protected",
                        color = LocalAppColors.current.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Don't worry — we never see your data. Only the SMS text with the amount masked is sent to Google Gemini using your own personal API key. Nothing is stored or shared by us.",
                        color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }

            if (localAiEnabled) {
                Spacer(Modifier.height(10.dp))

                // API key entry card
                Column(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalAppColors.current.cardBg)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gemini API Key", color = LocalAppColors.current.secondaryText, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp,
                            modifier = Modifier.weight(1f))
                        if (localApiKey.isNotBlank()) {
                            Text(if (apiKeyVisible) "Hide" else "Show",
                                color = LocalAppColors.current.accentGold, fontSize = 11.sp,
                                modifier = Modifier.clickable { apiKeyVisible = !apiKeyVisible })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value                = localApiKey,
                        onValueChange        = { localApiKey = it },
                        visualTransformation = if (apiKeyVisible || localApiKey.isBlank())
                                                   VisualTransformation.None
                                               else PasswordVisualTransformation('•'),
                        textStyle            = androidx.compose.ui.text.TextStyle(
                            color = LocalAppColors.current.primaryText, fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        singleLine           = true,
                        modifier             = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalAppColors.current.cardSurface)
                            .border(0.5.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) { innerTextField ->
                        if (localApiKey.isBlank()) {
                            Text("Paste your API key here", color = LocalAppColors.current.secondaryText.copy(alpha = 0.5f),
                                fontSize = 13.sp)
                        }
                        innerTextField()
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (localApiKey.isNotBlank()) LocalAppColors.current.accentGold else LocalAppColors.current.cardSurface)
                            .border(0.5.dp,
                                if (localApiKey.isNotBlank()) LocalAppColors.current.accentGold else LocalAppColors.current.cardBorder,
                                RoundedCornerShape(10.dp))
                            .clickable(enabled = localApiKey.isNotBlank()) {
                                onAiSettingsChanged(true, localApiKey)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save API Key",
                            color = if (localApiKey.isNotBlank()) Color.Black else LocalAppColors.current.secondaryText,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // How to get a free key
                Column(modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalAppColors.current.accentGold.copy(alpha = 0.07f))
                    .border(0.5.dp, LocalAppColors.current.accentGold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text("HOW TO GET A FREE API KEY", color = LocalAppColors.current.accentGold, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("1.", color = LocalAppColors.current.accentGold, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Text(
                            "Visit aistudio.google.com",
                            color = LocalAppColors.current.accentGold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                val url = Uri.parse("https://aistudio.google.com")
                                val chromeIntent = Intent(Intent.ACTION_VIEW, url).apply {
                                    setPackage("com.android.chrome")
                                }
                                if (chromeIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(chromeIntent)
                                } else {
                                    android.app.AlertDialog.Builder(context)
                                        .setTitle("⚠️ Chrome Recommended")
                                        .setMessage("For the best experience and to avoid authentication errors, please open this link in Chrome.\n\nOpening in another browser may cause sign-in to fail on Google AI Studio.")
                                        .setPositiveButton("Open Anyway") { _, _ ->
                                            context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                            }
                        )
                    }
                    listOf(
                        "2" to "Sign in with any Gmail account",
                        "3" to "Tap \"Get API key\" → \"Create API key\"",
                        "4" to "Copy the key and paste it above"
                    ).forEach { (num, step) ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("$num.", color = LocalAppColors.current.accentGold, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                            Text(step, color = LocalAppColors.current.secondaryText, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = LocalAppColors.current.accentGold.copy(alpha = 0.2f), thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("✅  Free: 1,500 requests/day", color = CreditColor, fontSize = 11.sp)
                    Text("✅  No credit or debit card needed", color = CreditColor, fontSize = 11.sp)
                    Text("✅  Only requires a Gmail account", color = CreditColor, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Refer a Friend screen ────────────────────────────────────────────────────
@Composable
fun ReferFriendScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                .clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Text("Refer a Friend", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎁", fontSize = 64.sp)
            Spacer(Modifier.height(20.dp))
            Text("Share with friends", color = LocalAppColors.current.primaryText, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Know someone who wants to track their expenses?\nShare Money Trail with them!",
                color = LocalAppColors.current.secondaryText, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "Track your expenses smartly with Money Trail! 💰\n" +
                            "Never miss where your money goes.")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.accentGold)
            ) {
                Text("Share App  →", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── About Us screen ──────────────────────────────────────────────────────────
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                .clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp)
            }
            Spacer(Modifier.width(14.dp))
            Text("About Us", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💰", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("Money Trail", color = LocalAppColors.current.primaryText, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Coming soon…", color = LocalAppColors.current.secondaryText, fontSize = 13.sp)
            }
        }
    }
}

// ─── Incoming overview card ───────────────────────────────────────────────────
@Composable
fun IncomingOverviewCard(
    total: Double,
    teasers: List<Triple<String, String, Double>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val show = LocalShowAmounts.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(
                    CreditColor.copy(alpha = 0.10f),
                    CreditColor.copy(alpha = 0.03f)
                ))
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(
                    CreditColor.copy(alpha = 0.55f),
                    CreditColor.copy(alpha = 0.10f)
                )),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("↓", color = CreditColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text("INCOMING", color = CreditColor, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Text("›", color = LocalAppColors.current.secondaryText.copy(alpha = 0.5f), fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(fmtAmt(total, show), color = LocalAppColors.current.primaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("tap to see breakdown", color = LocalAppColors.current.secondaryText, fontSize = 9.sp)
    }
}

// ─── Spent overview card ──────────────────────────────────────────────────────
@Composable
fun SpentOverviewCard(
    total: Double,
    teasers: List<Triple<String, String, Double>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val show = LocalShowAmounts.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(
                    DebitColor.copy(alpha = 0.10f),
                    DebitColor.copy(alpha = 0.03f)
                ))
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(
                    DebitColor.copy(alpha = 0.55f),
                    DebitColor.copy(alpha = 0.10f)
                )),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("↑", color = DebitColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text("SPENT", color = DebitColor, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Text("›", color = LocalAppColors.current.secondaryText.copy(alpha = 0.5f), fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(fmtAmt(total, show), color = LocalAppColors.current.primaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("tap to see breakdown", color = LocalAppColors.current.secondaryText, fontSize = 9.sp)
    }
}

// ─── Spent Screen ─────────────────────────────────────────────────────────────
@Composable
fun SpentScreen(
    transactions: List<ParsedWithCategory>,
    onBack: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onCustomCategoryClick: (SubCategory) -> Unit
) {
    val customCategories = LocalCustomCategories.current
    // Transfers and CC bill payments excluded — neither is an actual expense
    val debits = transactions.filter { it.sms.type == TxType.DEBIT && it.category != Category.TRANSFER && it.category != Category.CC_PAYMENT }
    val totalSpent = debits.sumOf { it.sms.amount }.takeIf { it > 0 } ?: 1.0

    // Built-in categories (exclude CUSTOM)
    val byCategory = debits
        .filter { it.category != Category.CUSTOM }
        .groupBy { it.category }
        .entries
        .sortedByDescending { (_, v) -> v.sumOf { it.sms.amount } }

    // Custom categories with transactions (matched via category == CUSTOM + txKey stored as one-time override)
    val byCustom = customCategories
        .map { sub -> sub to debits.filter { it.category == Category.CUSTOM && it.subCategory.id == sub.id } }
        .filter { (_, txns) -> txns.isNotEmpty() }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💸", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Spent", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${debits.size} transactions  •  ${fmtAmt(totalSpent, LocalShowAmounts.current)} total",
                    color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                )
            }
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        if (debits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No spending in this period.", color = LocalAppColors.current.secondaryText)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            // Built-in category grid (2 columns)
            val rows = byCategory.chunked(2)
            items(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (cat, txns) ->
                        val amt   = txns.sumOf { it.sms.amount }
                        val pct   = (amt / totalSpent).toFloat().coerceIn(0f, 1f)
                        CategoryGridCard(
                            modifier  = Modifier.weight(1f),
                            category  = cat,
                            amount    = amt,
                            count     = txns.size,
                            percentage = pct,
                            onClick   = { onCategoryClick(cat) }
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            // MY CATEGORIES section
            if (byCustom.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "MY CATEGORIES",
                        color = LocalAppColors.current.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val customRows = byCustom.chunked(2)
                items(customRows) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (sub, txns) ->
                            val amt   = txns.sumOf { it.sms.amount }
                            val pct   = (amt / totalSpent).toFloat().coerceIn(0f, 1f)
                            val color = Color(sub.colorValue)
                            CustomCategoryGridCard(
                                modifier   = Modifier.weight(1f),
                                subCat     = sub,
                                amount     = amt,
                                count      = txns.size,
                                percentage = pct,
                                color      = color,
                                onClick    = { onCustomCategoryClick(sub) }
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─── Incoming Screen ───────────────────────────────────────────────────────────
@Composable
fun IncomingScreen(
    transactions: List<ParsedWithCategory>,
    onBack: () -> Unit,
    onItemClick: (Category, SubCategory?) -> Unit
) {
    val credits = transactions.filter { it.sms.type == TxType.CREDIT }
    // Self-transfers excluded from income total — moving money between own accounts isn't income
    val incomeCredits  = credits.filter { it.category != Category.TRANSFER }
    val totalReceived  = incomeCredits.sumOf { it.sms.amount }.takeIf { it > 0 } ?: 1.0
    val transferAmount = credits.filter { it.category == Category.TRANSFER }.sumOf { it.sms.amount }

    // Group income credits by source (bank / merchant) for the overview rows
    val incomeRows: List<Triple<String, String, List<ParsedWithCategory>>> = credits
        .filter { it.category == Category.INCOME }
        .groupBy { it.sms.merchant ?: it.sms.bank }
        .entries
        .sortedByDescending { (_, txns) -> txns.sumOf { it.sms.amount } }
        .map { (label, txns) -> Triple("💰", label, txns) }

    // TRANSFER credits (shown separately, not in income total)
    val transferTxns = credits.filter { it.category == Category.TRANSFER }

    // UNCATEGORIZED credits
    val uncatCredits = credits.filter { it.category == Category.UNCATEGORIZED }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📥", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Incoming", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                val show = LocalShowAmounts.current
                Text(
                    "${incomeCredits.size} income transactions  •  ${fmtAmt(totalReceived, show)}",
                    color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                )
                if (transferAmount > 0) {
                    Text(
                        "+ ${fmtAmt(transferAmount, show)} self-transfers (excluded)",
                        color = LocalAppColors.current.secondaryText.copy(alpha = 0.6f), fontSize = 11.sp
                    )
                }
            }
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        if (credits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No incoming transactions in this period.", color = LocalAppColors.current.secondaryText)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Income source rows (grouped by merchant / bank)
            items(incomeRows) { (icon, label, txns) ->
                val amt = txns.sumOf { it.sms.amount }
                val pct = (amt / totalReceived).toFloat().coerceIn(0f, 1f)
                IncomeSubCatRow(
                    icon       = icon,
                    label      = label,
                    amount     = amt,
                    count      = txns.size,
                    percentage = pct,
                    color      = CreditColor,
                    onClick    = { onItemClick(Category.INCOME, null) }
                )
            }

            // Self-transfer section — shown separately, not part of income total
            if (transferTxns.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
                        Text(
                            "  SELF TRANSFERS — not counted in total  ",
                            color = LocalAppColors.current.secondaryText.copy(alpha = 0.5f), fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
                    }
                    val amt = transferTxns.sumOf { it.sms.amount }
                    IncomeSubCatRow(
                        icon       = categoryIcon(Category.TRANSFER),
                        label      = Category.TRANSFER.displayName,
                        amount     = amt,
                        count      = transferTxns.size,
                        percentage = 0f,
                        color      = LocalAppColors.current.secondaryText.copy(alpha = 0.6f),
                        onClick    = { onItemClick(Category.TRANSFER, null) }
                    )
                }
            }

            // Uncategorized credits row
            if (uncatCredits.isNotEmpty()) {
                item {
                    val amt = uncatCredits.sumOf { it.sms.amount }
                    val pct = (amt / totalReceived).toFloat().coerceIn(0f, 1f)
                    IncomeSubCatRow(
                        icon       = categoryIcon(Category.UNCATEGORIZED),
                        label      = Category.UNCATEGORIZED.displayName,
                        amount     = amt,
                        count      = uncatCredits.size,
                        percentage = pct,
                        color      = categoryColor(Category.UNCATEGORIZED),
                        onClick    = { onItemClick(Category.UNCATEGORIZED, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun IncomeSubCatRow(
    icon: String,
    label: String,
    amount: Double,
    count: Int,
    percentage: Float,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.12f))
                        .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 22.sp) }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, color = LocalAppColors.current.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("$count transaction${if (count != 1) "s" else ""}", color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtAmt(amount, LocalShowAmounts.current), color = CreditColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("%.0f%%".format(percentage * 100), color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text("›", color = LocalAppColors.current.secondaryText, fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.cardSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentage).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(color)
                )
            }
        }
    }
}

// ─── Category grid card (2-column layout) ─────────────────────────────────────
@Composable
fun CategoryGridCard(
    modifier: Modifier = Modifier,
    category: Category,
    amount: Double,
    count: Int,
    percentage: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: category icon + arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor(category).copy(alpha = 0.15f))
                        .border(1.dp, categoryColor(category).copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(categoryIcon(category), fontSize = 20.sp) }
                Text("›", color = LocalAppColors.current.secondaryText, fontSize = 20.sp)
            }

            // Bottom section: amount, name, count, progress bar
            Column {
                Text(
                    fmtAmt(amount, LocalShowAmounts.current),
                    color = LocalAppColors.current.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    category.displayName,
                    color = categoryColor(category), fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$count transaction${if (count != 1) "s" else ""}",
                    color = LocalAppColors.current.secondaryText, fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                // Progress bar
                Box(
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                        .clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.cardSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percentage).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(categoryColor(category))
                    )
                }
            }
        }
    }
}

@Composable
fun CustomCategoryGridCard(
    modifier: Modifier = Modifier,
    subCat: SubCategory,
    amount: Double,
    count: Int,
    percentage: Float,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.15f))
                        .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(subCat.icon, fontSize = 20.sp) }
                Text("›", color = LocalAppColors.current.secondaryText, fontSize = 20.sp)
            }
            Column {
                Text(fmtAmt(amount, LocalShowAmounts.current), color = LocalAppColors.current.primaryText,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subCat.displayName, color = color, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count transaction${if (count != 1) "s" else ""}", color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.cardSurface)) {
                    Box(modifier = Modifier.fillMaxWidth(percentage).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(color))
                }
            }
        }
    }
}

@Composable
fun CategoryRow(
    category: Category, amount: Double, count: Int,
    percentage: Float, onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor(category).copy(alpha = 0.12f))
                        .border(1.dp, categoryColor(category).copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(categoryIcon(category), fontSize = 22.sp) }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(category.displayName, color = LocalAppColors.current.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("$count transaction${if (count != 1) "s" else ""}", color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtAmt(amount, LocalShowAmounts.current), color = LocalAppColors.current.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("%.0f%%".format(percentage * 100), color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                }

                Spacer(Modifier.width(8.dp))
                Text("›", color = LocalAppColors.current.secondaryText, fontSize = 22.sp)
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.cardSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentage).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(categoryColor(category))
                )
            }
        }
    }
}

// ─── Transaction card ─────────────────────────────────────────────────────────
private val smsDateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionCard(
    item: ParsedWithCategory,
    showSubcategoryAsPrimary: Boolean = false,
    allDebits: List<ParsedWithCategory> = emptyList(),
    settlements: Map<String, Triple<String, String, Double>> = emptyMap(),
    debitSettledAmount: Double = 0.0,
    onSettlementChanged: () -> Unit = {},
    aiApiKey: String? = null,
    onAiSetup: () -> Unit = {},
    customPayeeName: String? = null,
    onPayeeNameSaved: ((String) -> Unit)? = null,
    note: String? = null,
    onNoteSaved: ((String) -> Unit)? = null,
    isRecurring: Boolean = false,
    isDuplicate: Boolean = false,
    onCategoryChange: (Category, SubCategory?, String?, Boolean, String?) -> Unit = { _, _, _, _, _ -> }
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val sms         = item.sms
    val amountColor = when {
        item.isVoided               -> LocalAppColors.current.secondaryText
        sms.type == TxType.DEBIT   -> DebitColor
        else                        -> CreditColor
    }
    val prefix      = if (sms.type == TxType.DEBIT) "- ₹" else "+ ₹"
    val dateStr     = remember(item.date) { smsDateFmt.format(Date(item.date)) }
    var expanded           by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showLinkSheet      by remember { mutableStateOf(false) }
    var aiSuggestion by remember { mutableStateOf<GeminiService.Suggestion?>(null) }
    var aiLoading    by remember { mutableStateOf(false) }
    var aiError      by remember { mutableStateOf<String?>(null) }

    val isCredit      = sms.type == TxType.CREDIT
    val settlementRec = if (isCredit) settlements[item.txKey] else null

    val merchantLabel = customPayeeName ?: sms.merchant ?: "Unknown"
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput      by remember(customPayeeName) { mutableStateOf(customPayeeName ?: "") }
    var showNoteDialog   by remember { mutableStateOf(false) }
    var noteInput        by remember(note) { mutableStateOf(note ?: "") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, if (item.isVoided) LocalAppColors.current.cardBorder else LocalAppColors.current.cardBorder, RoundedCornerShape(14.dp))
            .then(if (item.isVoided) Modifier.alpha(0.5f) else Modifier)
            .clickable {
                expanded = !expanded
                if (!expanded) { aiSuggestion = null; aiLoading = false; aiError = null }
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val catColor = categoryColor(item.category)
                // Show the user-selected icon tag if it differs from the default category icon
                val displayIcon = item.subCategory.icon.takeIf { it != categoryIcon(item.category) }
                    ?: categoryIcon(item.category)
                Box(
                    modifier = Modifier
                        .size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(catColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(displayIcon, fontSize = 20.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val tagLabel = if (item.subCategory.id != item.category.name)
                        item.subCategory.displayName
                    else
                        item.category.displayName
                    // When payee is known (named or extracted), show it bold on top.
                    // When payee is truly unknown, show tag bold so something meaningful is primary.
                    val payeeIsKnown = merchantLabel != "Unknown"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isManualOverride) Text("📌 ", fontSize = 9.sp)
                        Text(
                            if (payeeIsKnown) merchantLabel else tagLabel,
                            color = LocalAppColors.current.primaryText, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        if (payeeIsKnown) tagLabel else merchantLabel,
                        color = LocalAppColors.current.secondaryText, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (item.creditCardId != null) {
                        // CC transaction — show bank + masked card number as a small badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Aureate.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💳 ", fontSize = 9.sp)
                            Text(
                                "${sms.bank} ••••${sms.accountTail}",
                                color = Aureate.copy(alpha = 0.85f), fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (sms.accountTail != null) {
                        Text("••••${sms.accountTail}", color = LocalAppColors.current.secondaryText.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                    if (note != null) {
                        Text(
                            "📝 $note",
                            color = LocalAppColors.current.accentGold.copy(alpha = 0.8f), fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val show = LocalShowAmounts.current
                    Text(
                        if (show) "$prefix%.0f".format(sms.amount) else "₹ ••••",
                        color = amountColor, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textDecoration = if (item.isVoided) TextDecoration.LineThrough else null
                    )
                    if (item.isVoided) {
                        Text("⊘ Excluded", color = LocalAppColors.current.secondaryText, fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                    }
                    if (isRecurring) Text("🔄 Recurring", color = LocalAppColors.current.accentGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    if (isDuplicate) Text("⚠️ Duplicate?", color = DebitColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    if (show && sms.foreignCurrency != null && sms.foreignAmount != null) {
                        Text(
                            "${sms.foreignCurrency} ${"%.2f".format(sms.foreignAmount)}",
                            color = LocalAppColors.current.secondaryText, fontSize = 10.sp
                        )
                    }
                    // Recovery badge for debit transactions
                    if (!isCredit && debitSettledAmount > 0 && show) {
                        Text(
                            "↩ ₹${"%.0f".format(debitSettledAmount)} recovered",
                            color = CreditColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Settlement badge for credit transactions
                    if (isCredit && settlementRec != null) {
                        Text("🔗 Settled", color = CreditColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(dateStr, color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))

                // ── Quick icon tag strip ───────────────────────────────────────
                val iconOptions = SubCategoryRules.ICON_OPTIONS[item.category]
                if (!iconOptions.isNullOrEmpty()) {
                    Text("TAG", color = LocalAppColors.current.secondaryText, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(iconOptions) { (emoji, label) ->
                            val isSel = item.subCategory.icon == emoji
                            val tint  = categoryColor(item.category)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) tint.copy(alpha = 0.15f) else LocalAppColors.current.cardSurface)
                                    .border(if (isSel) 1.dp else 0.5.dp,
                                        if (isSel) tint else LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val tag = if (isSel) null
                                        else SubCategoryRules.resolveIconTag(emoji, item.category, categoryIcon(item.category))
                                        // Route to the right store so future same-source transactions inherit the tag
                                        when {
                                            item.hasOneTimeOverride  -> onCategoryChange(item.category, tag, null, true, null)
                                            item.sms.merchant != null -> onCategoryChange(item.category, tag, null, false, null)
                                            item.upiId != null        -> onCategoryChange(item.category, tag, null, false, item.upiId)
                                            else                      -> onCategoryChange(item.category, tag, null, true, null)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(emoji, fontSize = 20.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(label,
                                    color = if (isSel) tint else LocalAppColors.current.secondaryText,
                                    fontSize = 9.sp, textAlign = TextAlign.Center,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2, lineHeight = 11.sp,
                                    modifier = Modifier.widthIn(max = 56.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))
                }

                // ── AI Suggestion (UNCATEGORIZED transactions only) ───────────
                if (item.category == Category.UNCATEGORIZED) {
                    when {
                        aiLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LocalAppColors.current.cardSurface)
                                    .border(0.5.dp, LocalAppColors.current.accentGold.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = LocalAppColors.current.accentGold, strokeWidth = 2.dp
                                    )
                                    Text("AI is analysing your transaction…",
                                        color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                                }
                            }
                        }
                        aiSuggestion != null -> {
                            val s = aiSuggestion!!
                            val suggestedSubCat = s.iconEmoji?.let {
                                SubCategoryRules.resolveIconTag(it, s.category, categoryIcon(s.category))
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LocalAppColors.current.accentGold.copy(alpha = 0.07f))
                                    .border(1.dp, LocalAppColors.current.accentGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("✨", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI SUGGESTS", color = LocalAppColors.current.accentGold, fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.iconEmoji ?: categoryIcon(s.category), fontSize = 22.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(s.category.displayName, color = LocalAppColors.current.primaryText,
                                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        if (suggestedSubCat != null) {
                                            Text(suggestedSubCat.displayName, color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("\"${s.reason}\"", color = LocalAppColors.current.secondaryText, fontSize = 11.sp,
                                    lineHeight = 15.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier.weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LocalAppColors.current.accentGold)
                                            .clickable {
                                                onCategoryChange(s.category, suggestedSubCat, null, true, null)
                                                aiSuggestion = null
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Apply", color = Color.Black,
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        modifier = Modifier.weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LocalAppColors.current.cardSurface)
                                            .border(0.5.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                                            .clickable { aiSuggestion = null }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Dismiss", color = LocalAppColors.current.secondaryText, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (aiApiKey != null) {
                                    Box(
                                        modifier = Modifier.weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LocalAppColors.current.cardSurface)
                                            .border(0.5.dp, LocalAppColors.current.accentGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                aiError = null
                                                scope.launch {
                                                    aiLoading = true
                                                    val result = GeminiService.suggest(aiApiKey, sms.rawText)
                                                    aiLoading = false
                                                    result.onSuccess { aiSuggestion = it }
                                                    result.onFailure { aiError = "Could not identify where this money was spent. Try again or categorise it manually." }
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✨  AI Suggest", color = LocalAppColors.current.accentGold,
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LocalAppColors.current.cardSurface)
                                            .border(0.5.dp, LocalAppColors.current.secondaryText.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                            .clickable { onAiSetup() }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✨  Enable AI Suggest", color = LocalAppColors.current.secondaryText,
                                            fontSize = 13.sp)
                                    }
                                }
                            }
                            if (aiError != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DebitColor.copy(alpha = 0.08f))
                                        .border(0.5.dp, DebitColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("🤖", fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        aiError!!,
                                        color = LocalAppColors.current.secondaryText,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))
                }

                Text("Source SMS", color = LocalAppColors.current.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(sms.rawText, color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 15.sp)
                Text(
                    "Bank: ${sms.bank}  •  ${sms.confidence}",
                    color = LocalAppColors.current.secondaryText.copy(alpha = 0.6f), fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                // Row 1: Change Category (full width)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LocalAppColors.current.cardSurface)
                        .border(0.5.dp, LocalAppColors.current.accentGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { showCategoryPicker = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎  Change Category",
                        color = LocalAppColors.current.accentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                // Row 2: Add Note + Name Payee (equal width)
                if (onNoteSaved != null || onPayeeNameSaved != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onNoteSaved != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LocalAppColors.current.cardSurface)
                                    .border(0.5.dp, if (note != null) LocalAppColors.current.accentGold.copy(alpha = 0.5f) else LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                                    .clickable { showNoteDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (note != null) "📝 Edit Note" else "📝 Add Note",
                                    color = if (note != null) LocalAppColors.current.accentGold else LocalAppColors.current.primaryText,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (onPayeeNameSaved != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LocalAppColors.current.cardSurface)
                                    .border(0.5.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                                    .clickable { showRenameDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (customPayeeName != null) "✏️  Rename" else "✏️  Name Payee",
                                    color = LocalAppColors.current.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Rename payee dialog
                if (showRenameDialog) {
                    val scopeNote = when {
                        item.upiId != null ->
                            "📲 Applies to ALL transactions from UPI ID:\n${item.upiId}"
                        sms.merchant != null ->
                            "🏪 Applies to ALL transactions from merchant:\n${sms.merchant}"
                        else ->
                            "📌 Applies only to this transaction (no UPI ID found)"
                    }
                    AlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        containerColor = LocalAppColors.current.cardSurface,
                        title = {
                            Text("Name this payee", color = LocalAppColors.current.primaryText,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = renameInput,
                                    onValueChange = { renameInput = it },
                                    placeholder = { Text("e.g. Kargil Vegetable Mart", color = LocalAppColors.current.secondaryText, fontSize = 13.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = LocalAppColors.current.accentGold,
                                        unfocusedBorderColor = LocalAppColors.current.cardBorder,
                                        focusedTextColor = LocalAppColors.current.primaryText,
                                        unfocusedTextColor = LocalAppColors.current.primaryText,
                                        cursorColor = LocalAppColors.current.accentGold
                                    )
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(scopeNote,
                                    color = if (item.upiId != null || sms.merchant != null) LocalAppColors.current.accentGold.copy(alpha = 0.8f) else LocalAppColors.current.secondaryText,
                                    fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (renameInput.isNotBlank()) {
                                    onPayeeNameSaved?.invoke(renameInput.trim())
                                }
                                showRenameDialog = false
                            }) {
                                Text("Save", color = LocalAppColors.current.accentGold, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = false }) {
                                Text("Cancel", color = LocalAppColors.current.secondaryText)
                            }
                        }
                    )
                }

                // Note dialog
                if (showNoteDialog) {
                    AlertDialog(
                        onDismissRequest = { showNoteDialog = false },
                        containerColor = LocalAppColors.current.cardSurface,
                        title = { Text("Add Note", color = LocalAppColors.current.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            OutlinedTextField(
                                value = noteInput,
                                onValueChange = { noteInput = it },
                                placeholder = { Text("e.g. office lunch reimbursement", color = LocalAppColors.current.secondaryText, fontSize = 13.sp) },
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LocalAppColors.current.accentGold, unfocusedBorderColor = LocalAppColors.current.cardBorder,
                                    focusedTextColor = LocalAppColors.current.primaryText, unfocusedTextColor = LocalAppColors.current.primaryText, cursorColor = LocalAppColors.current.accentGold
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onNoteSaved?.invoke(noteInput.trim())
                                showNoteDialog = false
                            }) { Text("Save", color = LocalAppColors.current.accentGold, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                if (note != null) { onNoteSaved?.invoke("") } // clear note
                                showNoteDialog = false
                            }) { Text(if (note != null) "Remove" else "Cancel", color = LocalAppColors.current.secondaryText) }
                        }
                    )
                }

                // Settlement UI — only for credit (incoming) transactions
                if (isCredit) {
                    Spacer(Modifier.height(8.dp))
                    if (settlementRec != null) {
                        // Already linked — show info + remove option
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CreditColor.copy(alpha = 0.08f))
                                .border(0.5.dp, CreditColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column {
                                Text(
                                    "🔗  Settles: ${settlementRec.second}",
                                    color = CreditColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Remove settlement link",
                                    color = DebitColor, fontSize = 11.sp,
                                    modifier = Modifier.clickable {
                                        UserPrefsStore.removeSettlement(context, item.txKey)
                                        onSettlementChanged()
                                    }
                                )
                            }
                        }
                    } else {
                        // Not linked — show link button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(LocalAppColors.current.cardSurface)
                                .border(0.5.dp, CreditColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable { showLinkSheet = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("🔗  Link to a shared expense",
                                color = CreditColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // ── Void / Restore ────────────────────────────────────────────
                val doVoid    = LocalVoidTransaction.current
                val doRestore = LocalRestoreTransaction.current
                Spacer(Modifier.height(8.dp))
                if (item.isVoided) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CreditColor.copy(alpha = 0.08f))
                            .border(0.5.dp, CreditColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickable { doRestore(item.txKey) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("↩", color = CreditColor, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Restore to calculations", color = CreditColor,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Currently excluded from all totals",
                                    color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalAppColors.current.cardSurface)
                            .border(0.5.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                            .clickable { doVoid(item.txKey) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⊘", color = LocalAppColors.current.secondaryText, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Exclude from calculations", color = LocalAppColors.current.secondaryText,
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Transaction stays visible but won't affect totals",
                                    color = LocalAppColors.current.secondaryText.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Debit: show all settlements made against this expense
                if (!isCredit && debitSettledAmount > 0) {
                    Spacer(Modifier.height(8.dp))
                    val show = LocalShowAmounts.current
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CreditColor.copy(alpha = 0.08f))
                            .border(0.5.dp, CreditColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                "↩  ${fmtAmt(debitSettledAmount, show)} recovered via settlements",
                                color = CreditColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                            )
                            if (show) {
                                val net = sms.amount - debitSettledAmount
                                Text(
                                    "Net expense: ${fmtAmt(net.coerceAtLeast(0.0), show)}",
                                    color = LocalAppColors.current.secondaryText, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            item      = item,
            onDismiss = { showCategoryPicker = false },
            onSave    = { newCat, newSubCat, keyword, isOneTime, upiId ->
                onCategoryChange(newCat, newSubCat, keyword, isOneTime, upiId)
                showCategoryPicker = false
            }
        )
    }

    if (showLinkSheet) {
        LinkSettlementSheet(
            creditItem  = item,
            allDebits   = allDebits,
            onDismiss   = { showLinkSheet = false },
            onLinked    = { showLinkSheet = false; onSettlementChanged() }
        )
    }
}

// ─── Settlement link picker ───────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSettlementSheet(
    creditItem: ParsedWithCategory,
    allDebits: List<ParsedWithCategory>,
    onDismiss: () -> Unit,
    onLinked: () -> Unit
) {
    val context    = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val show       = LocalShowAmounts.current
    var searchQuery by remember { mutableStateOf("") }

    val debits = remember(allDebits) {
        allDebits.sortedByDescending { it.date }
    }
    val filteredDebits = remember(debits, searchQuery) {
        if (searchQuery.isBlank()) debits
        else {
            val q = searchQuery.trim().lowercase()
            debits.filter { debit ->
                "%.0f".format(debit.sms.amount).contains(q) ||
                debit.sms.merchant?.lowercase()?.contains(q) == true ||
                debit.category.displayName.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = LocalAppColors.current.appBg,
        dragHandle       = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 32.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Link to an expense", color = LocalAppColors.current.primaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "+ ₹${"%.0f".format(creditItem.sms.amount)} received — which expense does this settle?",
                        color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        .background(LocalAppColors.current.cardBg).clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = LocalAppColors.current.secondaryText, fontSize = 14.sp) }
            }

            Spacer(Modifier.height(12.dp))

            // Amount / merchant search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LocalAppColors.current.cardSurface)
                    .border(1.dp, if (searchQuery.isNotBlank()) LocalAppColors.current.accentGold.copy(alpha = 0.5f) else LocalAppColors.current.cardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = LocalAppColors.current.primaryText, fontSize = 14.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search by amount or merchant…", color = LocalAppColors.current.secondaryText, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        "✕", color = LocalAppColors.current.secondaryText, fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { searchQuery = "" }
                            .padding(4.dp)
                    )
                }
            }

            // Result count hint
            if (searchQuery.isNotBlank()) {
                Text(
                    "${filteredDebits.size} of ${debits.size} transactions",
                    color = LocalAppColors.current.secondaryText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

            if (filteredDebits.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isBlank()) "No expense transactions found."
                        else "No match for \"$searchQuery\"",
                        color = LocalAppColors.current.secondaryText
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDebits) { debit ->
                        val dateStr = remember(debit.date) { smsDateFmt.format(Date(debit.date)) }
                        val label   = debit.sms.merchant
                            ?: "${debit.category.displayName} (₹${"%.0f".format(debit.sms.amount)})"
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(LocalAppColors.current.cardBg)
                                .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    UserPrefsStore.saveSettlement(
                                        context      = context,
                                        creditTxKey  = creditItem.txKey,
                                        debitTxKey   = debit.txKey,
                                        description  = label,
                                        creditAmount = creditItem.sms.amount
                                    )
                                    onLinked()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                        .background(categoryColor(debit.category).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) { Text(categoryIcon(debit.category), fontSize = 18.sp) }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, color = LocalAppColors.current.primaryText, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${debit.category.displayName}  •  $dateStr",
                                        color = LocalAppColors.current.secondaryText, fontSize = 11.sp
                                    )
                                }
                                Text(
                                    "- ₹${"%.0f".format(debit.sms.amount)}",
                                    color = DebitColor, fontSize = 14.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Two-step category + subcategory picker ───────────────────────────────────
private enum class RuleMatchType { MERCHANT, UPI, KEYWORD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    item: ParsedWithCategory,
    onDismiss: () -> Unit,
    onSave: (Category, SubCategory?, String?, Boolean, String?) -> Unit
) {
    val sheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detectedUpiId = remember(item.sms.rawText) { extractUpiId(item.sms.rawText) }

    var step                by remember { mutableIntStateOf(1) }
    var chosenCategory      by remember { mutableStateOf(item.category) }
    var pendingCategory     by remember { mutableStateOf<Category?>(null) }
    var pendingSubCat       by remember { mutableStateOf<SubCategory?>(null) }
    var showKeywordDialog   by remember { mutableStateOf(false) }
    var isOneTime           by remember { mutableStateOf(false) }
    var showCreateInPicker  by remember { mutableStateOf(false) }
    var ruleMatchType     by remember {
        mutableStateOf(
            when {
                item.sms.merchant != null -> RuleMatchType.MERCHANT
                detectedUpiId != null     -> RuleMatchType.UPI
                else                      -> RuleMatchType.KEYWORD
            }
        )
    }

    fun commitSave(cat: Category, subCat: SubCategory?) {
        when {
            isOneTime -> onSave(cat, subCat, null, true, null)
            ruleMatchType == RuleMatchType.MERCHANT && item.sms.merchant != null ->
                onSave(cat, subCat, null, false, null)
            ruleMatchType == RuleMatchType.UPI && detectedUpiId != null ->
                onSave(cat, subCat, null, false, detectedUpiId)
            else -> { pendingCategory = cat; pendingSubCat = subCat; showKeywordDialog = true }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = LocalAppColors.current.cardBg,
        scrimColor       = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.cardBorder))
            }
        }
    ) {
        if (step == 1) {
            // ── Step 1: Pick main category ────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                Spacer(Modifier.height(8.dp))
                Text("Change Category", color = LocalAppColors.current.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(item.sms.merchant ?: item.sms.bank, color = LocalAppColors.current.secondaryText, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(14.dp))

                // ── Rule vs One-Time toggle ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalAppColors.current.cardBg)
                        .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("⚡  Set Rule" to false, "✦  One-Time" to true).forEach { (label, value) ->
                        val sel  = isOneTime == value
                        val tint = if (value) LocalAppColors.current.accentGold else CreditColor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (sel) tint.copy(alpha = 0.13f) else Color.Transparent)
                                .then(if (sel) Modifier.border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(9.dp)) else Modifier)
                                .clickable { isOneTime = value }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label,
                                color = if (sel) tint else LocalAppColors.current.secondaryText,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isOneTime)
                        "Only moves this transaction. No rule is saved — other transactions are not affected."
                    else
                        "Saves a rule — future transactions matched by the selected identifier will auto-categorize.",
                    color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 15.sp
                )

                // "Match by" chip row — only in rule mode
                if (!isOneTime) {
                    Spacer(Modifier.height(12.dp))
                    Text("Match by:", color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data class RuleChip(val type: RuleMatchType, val icon: String, val label: String, val available: Boolean)
                        listOf(
                            RuleChip(RuleMatchType.MERCHANT, "🏪", "Merchant", item.sms.merchant != null),
                            RuleChip(RuleMatchType.UPI,      "📲", detectedUpiId ?: "UPI ID", detectedUpiId != null),
                            RuleChip(RuleMatchType.KEYWORD,  "🔑", "Keyword", true)
                        ).forEach { chip ->
                            val sel   = ruleMatchType == chip.type
                            val tint  = if (chip.available) CreditColor else Color(0xFF444444)
                            val alpha = if (chip.available) 1f else 0.4f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) tint.copy(alpha = 0.15f) else LocalAppColors.current.cardBg)
                                    .border(if (sel) 1.dp else 0.5.dp,
                                        if (sel) tint.copy(alpha = 0.6f) else LocalAppColors.current.cardBorder,
                                        RoundedCornerShape(20.dp))
                                    .then(if (chip.available) Modifier.clickable { ruleMatchType = chip.type } else Modifier)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(chip.icon, fontSize = 12.sp, modifier = Modifier.alpha(alpha))
                                    Text(chip.label,
                                        color = if (sel) tint else LocalAppColors.current.secondaryText.copy(alpha = alpha),
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 120.dp))
                                }
                            }
                        }
                    }

                    // Learning notice based on selected rule type
                    val noticeText = when {
                        ruleMatchType == RuleMatchType.MERCHANT && item.sms.merchant != null ->
                            "All future \"${item.sms.merchant}\" transactions will follow this choice automatically"
                        ruleMatchType == RuleMatchType.UPI && detectedUpiId != null ->
                            "All future payments to \"$detectedUpiId\" will follow this choice automatically"
                        ruleMatchType == RuleMatchType.KEYWORD ->
                            "You'll enter a keyword — any SMS containing it will be auto-categorized"
                        else -> null
                    }
                    if (noticeText != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)).background(CreditColor.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("⚡", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(noticeText, color = CreditColor, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val allCategories = Category.entries.filter { it != Category.UNCATEGORIZED && it != Category.CUSTOM }
                allCategories.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cat ->
                            val sel = cat == item.category
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) categoryColor(cat).copy(alpha = 0.18f) else LocalAppColors.current.cardSurface)
                                    .border(if (sel) 1.5.dp else 0.5.dp,
                                        if (sel) categoryColor(cat) else LocalAppColors.current.cardBorder, RoundedCornerShape(12.dp))
                                    .clickable { chosenCategory = cat; step = 2 }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(categoryIcon(cat), fontSize = 20.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(cat.displayName,
                                        color = if (sel) categoryColor(cat) else LocalAppColors.current.primaryText,
                                        fontSize = 9.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Custom categories ──────────────────────────────────────────
                val customCats = LocalCustomCategories.current

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                Text("MY CATEGORIES", color = LocalAppColors.current.secondaryText, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))

                val customTiles: List<SubCategory?> = customCats + listOf(null) // null = "New" tile
                customTiles.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { customCat ->
                            if (customCat == null) {
                                // "New Category" tile
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LocalAppColors.current.cardBg)
                                        .border(1.dp, LocalAppColors.current.accentGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .clickable { showCreateInPicker = true }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("＋", fontSize = 20.sp, color = LocalAppColors.current.accentGold)
                                        Spacer(Modifier.height(4.dp))
                                        Text("New", color = LocalAppColors.current.accentGold, fontSize = 9.sp)
                                    }
                                }
                            } else {
                                val sel   = item.category == Category.CUSTOM && item.subCategory.icon == customCat.icon
                                val color = Color(customCat.colorValue)
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) color.copy(alpha = 0.18f) else LocalAppColors.current.cardSurface)
                                        .border(if (sel) 1.5.dp else 0.5.dp,
                                            if (sel) color else LocalAppColors.current.cardBorder, RoundedCornerShape(12.dp))
                                        .clickable { commitSave(Category.CUSTOM, customCat) }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(customCat.icon, fontSize = 20.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(customCat.displayName,
                                            color = if (sel) color else LocalAppColors.current.primaryText,
                                            fontSize = 9.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (showCreateInPicker) {
                    val pickerContext = LocalContext.current
                    val onChanged    = LocalOnCustomCategoryChanged.current
                    CreateCategoryDialog(
                        onDismiss = { showCreateInPicker = false },
                        onCreate  = { newCat ->
                            UserPrefsStore.saveCustomCategory(pickerContext, newCat)
                            onChanged()
                        }
                    )
                }
            }
        } else {
            // ── Step 2: Pick an icon tag (optional, just for visual identification) ────
            val iconOptions = SubCategoryRules.ICON_OPTIONS[chosenCategory] ?: emptyList()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                            .background(LocalAppColors.current.cardBorder).clickable { step = 1 },
                        contentAlignment = Alignment.Center
                    ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 16.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text(categoryIcon(chosenCategory), fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chosenCategory.displayName, color = LocalAppColors.current.primaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Choose a tag icon (optional)", color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (iconOptions.isNotEmpty()) {
                    // Icon grid — 3 columns
                    iconOptions.chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (emoji, label) ->
                                val sel = pendingSubCat?.icon == emoji
                                Box(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (sel) categoryColor(chosenCategory).copy(alpha = 0.18f)
                                            else LocalAppColors.current.cardSurface
                                        )
                                        .border(
                                            if (sel) 1.5.dp else 0.5.dp,
                                            if (sel) categoryColor(chosenCategory) else LocalAppColors.current.cardBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            pendingSubCat = if (sel) null
                                            else SubCategoryRules.resolveIconTag(emoji, chosenCategory, categoryIcon(chosenCategory))
                                        }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(emoji, fontSize = 22.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(label,
                                            color = if (sel) categoryColor(chosenCategory) else LocalAppColors.current.primaryText,
                                            fontSize = 9.sp, textAlign = TextAlign.Center,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2, lineHeight = 12.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(categoryColor(chosenCategory).copy(alpha = 0.12f))
                        .border(1.dp, categoryColor(chosenCategory).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable { commitSave(chosenCategory, pendingSubCat) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (pendingSubCat != null) "Save  ${pendingSubCat!!.icon} ${pendingSubCat!!.displayName}"
                        else "Save without icon",
                        color = categoryColor(chosenCategory), fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { commitSave(chosenCategory, null) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Skip", color = LocalAppColors.current.secondaryText, fontSize = 13.sp) }
            }
        }
    }

    // Keyword dialog — only appears when user chose "Keyword" rule type
    if (showKeywordDialog) {
        var keyword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                onSave(pendingCategory ?: item.category, pendingSubCat, null, false, null)
                showKeywordDialog = false
            },
            containerColor = LocalAppColors.current.cardBg,
            title = { Text("Enter Keyword Rule", color = LocalAppColors.current.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a keyword from this SMS to auto-categorize similar transactions in future.",
                        color = LocalAppColors.current.secondaryText, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(item.sms.rawText.take(130).let { if (it.length == 130) "$it…" else it },
                        color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(LocalAppColors.current.appBg).padding(10.dp))
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = keyword, onValueChange = { keyword = it },
                        label = { Text("Keyword (e.g. NETFLIX, SWIGGY)", color = LocalAppColors.current.secondaryText, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalAppColors.current.accentGold, unfocusedBorderColor = LocalAppColors.current.cardBorder,
                            focusedTextColor = LocalAppColors.current.primaryText, unfocusedTextColor = LocalAppColors.current.primaryText,
                            cursorColor = LocalAppColors.current.accentGold, focusedLabelColor = LocalAppColors.current.accentGold, unfocusedLabelColor = LocalAppColors.current.secondaryText
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onSave(pendingCategory ?: item.category, pendingSubCat, keyword.trim().ifEmpty { null }, false, null)
                            showKeywordDialog = false
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(pendingCategory ?: item.category, pendingSubCat, keyword.trim().ifEmpty { null }, false, null)
                    showKeywordDialog = false
                }) { Text("Save Rule", color = LocalAppColors.current.accentGold, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSave(pendingCategory ?: item.category, pendingSubCat, null, false, null)
                    showKeywordDialog = false
                }) { Text("Skip", color = LocalAppColors.current.secondaryText) }
            }
        )
    }
}

// ─── Permission screen ────────────────────────────────────────────────────────
@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📩", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text("SMS Permission Required", color = LocalAppColors.current.primaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "This app reads your bank SMS to auto-track expenses.\nAll data stays on your device — nothing is uploaded.",
            color = LocalAppColors.current.secondaryText, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(LocalAppColors.current.accentGold)
                .clickable { onRequest() }
                .padding(horizontal = 32.dp, vertical = 15.dp)
        ) {
            Text("Grant SMS Permission", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ─── Create Category dialog ───────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCreate:  (SubCategory) -> Unit
) {
    var name          by remember { mutableStateOf("") }
    var selectedIcon  by remember { mutableStateOf("📁") }
    var selectedColor by remember { mutableStateOf(0xFFFFD66BL) }

    val presetIcons = listOf(
        "🏠", "🐾", "💼", "🎯", "🎁", "🏋️", "🔧", "📱",
        "🌱", "🍕", "🎮", "🏊", "🛒", "💰", "🎨", "⚽",
        "🐕", "🧘", "🎵", "📷", "🚀", "💡"
    )
    val presetColors = listOf(
        0xFFFF6B6BL, 0xFFFF9F43L, 0xFFFECA57L, 0xFF00CEC9L,
        0xFF74B9FFL, 0xFF6C5CE7L, 0xFF00B894L, 0xFFFF85B3L,
        0xFFD980FAL, 0xFF636E72L, 0xFFFFD66BL, 0xFF1DD1A1L
    )

    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = LocalAppColors.current.cardBg,
        tonalElevation    = 0.dp,
        title = {
            Text("New Category", color = LocalAppColors.current.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                // Preview badge + name field
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(selectedColor).copy(alpha = 0.2f))
                            .border(1.dp, Color(selectedColor).copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(selectedIcon, fontSize = 26.sp) }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value         = name,
                        onValueChange = { if (it.length <= 20) name = it },
                        label         = { Text("Category name") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = LocalAppColors.current.accentGold,
                            focusedLabelColor    = LocalAppColors.current.accentGold,
                            cursorColor          = LocalAppColors.current.accentGold,
                            focusedTextColor     = LocalAppColors.current.primaryText,
                            unfocusedTextColor   = LocalAppColors.current.primaryText,
                            unfocusedBorderColor = LocalAppColors.current.cardBorder,
                            unfocusedLabelColor  = LocalAppColors.current.secondaryText
                        )
                    )
                }
                Spacer(Modifier.height(16.dp))

                // Icon picker
                Text("Icon", color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetIcons) { icon ->
                        val sel = icon == selectedIcon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) LocalAppColors.current.accentGold.copy(alpha = 0.15f) else LocalAppColors.current.cardSurface)
                                .border(if (sel) 1.5.dp else 0.5.dp,
                                    if (sel) LocalAppColors.current.accentGold else LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) { Text(icon, fontSize = 20.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Color picker
                Text("Color", color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetColors) { colorVal ->
                        val sel = colorVal == selectedColor
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(colorVal))
                                .then(if (sel) Modifier.border(2.5.dp, LocalAppColors.current.primaryText, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { selectedColor = colorVal }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (name.isNotBlank()) {
                        onCreate(SubCategory("CCAT_${System.currentTimeMillis()}", name.trim(), selectedIcon, selectedColor))
                        onDismiss()
                    }
                },
                enabled  = name.isNotBlank()
            ) { Text("Create", color = if (name.isNotBlank()) LocalAppColors.current.accentGold else LocalAppColors.current.secondaryText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = LocalAppColors.current.secondaryText) }
        }
    )
}

// ─── SMS loading ──────────────────────────────────────────────────────────────
// Returns raw parsed SMS + date only; categorization is done in composition
// so changing user overrides instantly re-categorizes without re-reading SMS.
fun readAndParseInbox(context: android.content.Context): List<Pair<ParsedSms, Long>> {
    val results = mutableListOf<Pair<ParsedSms, Long>>()
    val seen    = HashSet<String>()  // dedup by body-hash + date to prevent double-counting

    // Read ALL received SMS (type=1) directly from content://sms instead of content://sms/inbox.
    // The /inbox sub-URI is a view that some OEM SMS apps (Samsung One UI, Xiaomi MIUI)
    // additionally filter by their own app-level categories — bank/transactional SMS can
    // disappear from it if the device SMS app classified them as Spam, Promotional, or
    // put them in a separate "Business" thread. content://sms with type=1 bypasses that
    // app-layer filter and returns every received message from the underlying SMS database.
    val cursor = context.contentResolver.query(
        Uri.parse("content://sms"),
        arrayOf("address", "body", "date"),
        "type = 1",   // 1 = MESSAGE_TYPE_INBOX (received)
        null,
        "date DESC"
    ) ?: return emptyList()

    cursor.use {
        val aIdx = it.getColumnIndex("address")
        val bIdx = it.getColumnIndex("body")
        val dIdx = it.getColumnIndex("date")
        if (aIdx < 0 || bIdx < 0) return emptyList()
        var n = 0
        while (it.moveToNext() && n < 3000) {
            n++
            val sender = it.getString(aIdx) ?: continue
            val body   = it.getString(bIdx) ?: continue
            val date   = if (dIdx >= 0) it.getLong(dIdx) else System.currentTimeMillis()
            val key    = "${date}_${body.hashCode()}"
            if (!seen.add(key)) continue   // skip any duplicate rows
            val parsed = SmsParser.parse(sender, body) ?: continue
            results.add(Pair(parsed, date))
        }
    }

    // Merge notification-captured messages (RCS/Chat from Samsung Messages, Google Messages, etc.)
    // Body hash deduplicates messages that arrived via BOTH SMS and notification.
    val smsBodyHashes = results.mapTo(HashSet()) { it.first.rawText.hashCode() }
    for ((sender, body, date) in SmsNotificationListener.loadCaptured(context)) {
        if (!smsBodyHashes.add(body.hashCode())) continue
        val parsed = SmsParser.parse(sender, body) ?: continue
        results.add(Pair(parsed, date))
    }

    return results.sortedByDescending { it.second }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN — Search
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SearchScreen(
    allTransactions: List<ParsedWithCategory>,
    payeeNames: Map<String, String> = emptyMap(),
    txNotes: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    onCategoryChange: (ParsedWithCategory, Category, SubCategory?, String?, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val results = remember(query, allTransactions, payeeNames) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            allTransactions.filter { tx ->
                val payeeKey = tx.upiId?.lowercase() ?: tx.sms.merchant?.lowercase()?.trim() ?: tx.txKey
                val displayName = payeeNames[payeeKey] ?: tx.sms.merchant ?: "Unknown"
                val amtStr = "%.0f".format(tx.sms.amount)
                val dateStr = fmt.format(Date(tx.date)).lowercase()
                displayName.lowercase().contains(q) ||
                tx.category.displayName.lowercase().contains(q) ||
                amtStr.contains(q) ||
                dateStr.contains(q) ||
                (tx.upiId?.lowercase()?.contains(q) == true) ||
                (txNotes[tx.txKey]?.lowercase()?.contains(q) == true)
            }.sortedByDescending { it.date }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search by payee, amount, date…", color = LocalAppColors.current.secondaryText, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LocalAppColors.current.accentGold, unfocusedBorderColor = LocalAppColors.current.cardBorder,
                    focusedTextColor = LocalAppColors.current.primaryText, unfocusedTextColor = LocalAppColors.current.primaryText, cursorColor = LocalAppColors.current.accentGold
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Text("✕", color = LocalAppColors.current.secondaryText, fontSize = 14.sp,
                            modifier = Modifier.clickable { query = "" }.padding(8.dp))
                    }
                }
            )
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        when {
            query.isBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Search across all your transactions", color = LocalAppColors.current.secondaryText, fontSize = 14.sp)
                        Text("by payee name, amount, date, or note", color = LocalAppColors.current.secondaryText.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
            results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🤷", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("No transactions found for \"$query\"", color = LocalAppColors.current.secondaryText, fontSize = 14.sp)
                    }
                }
            }
            else -> {
                Text(
                    "${results.size} result${if (results.size != 1) "s" else ""}",
                    color = LocalAppColors.current.secondaryText, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(results, key = { it.txKey }) { item ->
                        val payeeKey = item.upiId?.lowercase() ?: item.sms.merchant?.lowercase()?.trim() ?: item.txKey
                        TransactionCard(
                            item             = item,
                            customPayeeName  = payeeNames[payeeKey],
                            note             = txNotes[item.txKey],
                            onCategoryChange = { cat, sub, kw, ot, upi ->
                                onCategoryChange(item, cat, sub, kw, ot, upi)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun DuplicatesScreen(
    allTransactions: List<ParsedWithCategory>,
    duplicateTxKeys: Set<String>,
    payeeNames: Map<String, String> = emptyMap(),
    txNotes: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    onCategoryChange: (ParsedWithCategory, Category, SubCategory?, String?, Boolean, String?) -> Unit
) {
    // Build duplicate pairs: group consecutive transactions that triggered each other
    val pairs: List<List<ParsedWithCategory>> = remember(allTransactions, duplicateTxKeys) {
        val sorted = allTransactions
            .filter { it.txKey in duplicateTxKeys }
            .sortedBy { it.date }
        val used = mutableSetOf<String>()
        val result = mutableListOf<List<ParsedWithCategory>>()
        for (i in sorted.indices) {
            if (sorted[i].txKey in used) continue
            val group = mutableListOf(sorted[i])
            used += sorted[i].txKey
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                if (b.txKey in used) continue
                val a = group[0]
                if (b.date - a.date <= 10 * 60 * 1000L &&
                    b.sms.amount == a.sms.amount &&
                    b.sms.bank == a.sms.bank &&
                    b.sms.type == a.sms.type) {
                    group += b
                    used += b.txKey
                }
            }
            result += group
        }
        result
    }

    Box(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(LocalAppColors.current.cardSurface)
                        .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(9.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 16.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Possible Duplicates", color = LocalAppColors.current.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${pairs.size} suspected duplicate pair${if (pairs.size != 1) "s" else ""}",
                        color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                }
            }

            // Info strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.accentGold.copy(alpha = 0.08f))
                    .border(0.5.dp, LocalAppColors.current.accentGold.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💡", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text("Same amount charged twice within 10 minutes. Tap ⊘ Exclude on one from each pair to fix your totals.",
                    color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.height(12.dp))

            if (pairs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("✅ No duplicates found", color = LocalAppColors.current.secondaryText, fontSize = 14.sp)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                    pairs.forEachIndexed { idx, group ->
                        item(key = "header_$idx") {
                            Spacer(Modifier.height(if (idx == 0) 0.dp else 16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DebitColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("⚠️ Pair ${idx + 1}",
                                        color = DebitColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.width(8.dp))
                                HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp, modifier = Modifier.weight(1f))
                            }
                        }
                        items(group, key = { it.txKey }) { item ->
                            val payeeKey = item.upiId?.lowercase() ?: item.sms.merchant?.lowercase()?.trim() ?: item.txKey
                            TransactionCard(
                                item             = item,
                                customPayeeName  = payeeNames[payeeKey],
                                note             = txNotes[item.txKey],
                                isDuplicate      = true,
                                onCategoryChange = { cat, sub, kw, ot, upi ->
                                    onCategoryChange(item, cat, sub, kw, ot, upi)
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Category helpers ─────────────────────────────────────────────────────────
fun categoryIcon(category: Category): String = when (category) {
    Category.FOOD          -> "🍔"
    Category.GROCERY       -> "🛒"
    Category.COMMUTE       -> "🚗"
    Category.TRAVEL        -> "✈️"
    Category.SHOPPING      -> "🛍️"
    Category.BILLS         -> "💡"
    Category.EDUCATION     -> "📚"
    Category.HEALTH        -> "💊"
    Category.FITNESS       -> "🏋️"
    Category.ENTERTAINMENT -> "🎬"
    Category.INCOME        -> "💰"
    Category.INVESTMENT    -> "📈"
    Category.CASH          -> "🏧"
    Category.TRANSFER      -> "↔️"
    Category.CC_PAYMENT    -> "💳"
    Category.UNCATEGORIZED -> "❓"
    Category.CUSTOM        -> "📂"
}

@Composable
fun categoryColor(category: Category): Color = when (category) {
    Category.INCOME        -> CreditColor   // semantic green for money in
    Category.TRANSFER      -> LocalAppColors.current.secondaryText // muted — self-transfer is neutral
    Category.CC_PAYMENT    -> Aureate
    Category.UNCATEGORIZED -> LocalAppColors.current.secondaryText
    else                   -> Aureate       // every other category: one gold
}

// ─── Payment Method helpers ───────────────────────────────────────────────────
@Composable
fun paymentMethodColor(method: PaymentMethod): Color = when (method) {
    PaymentMethod.OTHER -> LocalAppColors.current.secondaryText
    else                -> Aureate
}

fun extractUpiId(rawText: String): String? {
    val regex = Regex("""[a-zA-Z0-9._-]{2,40}@[a-zA-Z][a-zA-Z0-9]{1,20}""")
    return regex.find(rawText)?.value?.lowercase()
}

fun detectPaymentMethod(rawText: String): PaymentMethod {
    val body = rawText.lowercase()
    return when {
        // Credit card — "avl limit" is the definitive Axis Bank CC signal
        // e.g. "Axis Bank Card no. XX4501 ... Avl Limit: INR 91135.65"
        body.contains("avl limit")    || body.contains("avl. limit")    ||
        body.contains("credit card")  || body.contains("creditcard")    ||
        body.contains("cc ending")    || body.contains("cc no")         ||
        body.contains("cc a/c")       -> PaymentMethod.CREDIT_CARD

        // Debit card — "avl bal" is the definitive Axis Bank DC signal
        // e.g. "Axis Bank Card no. XX9120 ... Avl Bal: INR ..."
        body.contains("avl bal")       || body.contains("avl. bal")     ||
        body.contains("avail bal")     || body.contains("available bal") ||
        body.contains("debit card")    || body.contains("debitcard")    ||
        body.contains("dc ending")     || body.contains("dc no")        ||
        body.contains("dc a/c")        -> PaymentMethod.DEBIT_CARD

        // Generic card — only when "card no" / "card number" is explicitly mentioned
        // (avoids false-positives on masked account numbers like XXXXXX8438)
        body.contains("card no")       || body.contains("card number")  ->
            if (body.contains("limit")) PaymentMethod.CREDIT_CARD else PaymentMethod.DEBIT_CARD

        body.contains("upi")           -> PaymentMethod.UPI

        // NACH = National Automated Clearing House (bank-account auto-debits: SIP, EMI, etc.)
        body.contains("nach")  || body.contains("neft")  ||
        body.contains("imps")  || body.contains("rtgs")  ||
        body.contains("net banking")   -> PaymentMethod.NET_BANKING

        else -> PaymentMethod.OTHER
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN 3 — Payment Methods overview
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun PaymentMethodsScreen(
    transactions: List<ParsedWithCategory>,
    selectedPeriod: Period,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit,
    customRangeStart: Long?,
    customRangeEnd: Long?,
    onMethodClick: (PaymentMethod) -> Unit
) {
    val debits     = transactions.filter { it.sms.type == TxType.DEBIT }
    val credits    = transactions.filter { it.sms.type == TxType.CREDIT }
    val totalSpent = debits.sumOf { it.sms.amount }.takeIf { it > 0.0 } ?: 1.0

    val byMethod = PaymentMethod.entries
        .map { m -> m to debits.filter { it.paymentMethod == m } }
        .filter { (_, txns) -> txns.isNotEmpty() }
        .sortedByDescending { (_, txns) -> txns.sumOf { it.sms.amount } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Period.entries) { p ->
                    val chipLabel = if (p == Period.CUSTOM && selectedPeriod == Period.CUSTOM && customRangeStart != null) {
                        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
                        "${fmt.format(Date(customRangeStart))} – ${fmt.format(Date(customRangeEnd ?: customRangeStart))}"
                    } else p.label
                    PeriodChip(chipLabel, selectedPeriod == p) {
                        if (p == Period.CUSTOM) onShowDatePicker() else onPeriodChange(p)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            SummaryCard(
                spent    = debits.filter  { it.category != Category.TRANSFER && it.category != Category.CC_PAYMENT }.sumOf { it.sms.amount },
                received = credits.filter { it.category != Category.TRANSFER && it.category != Category.CC_PAYMENT }.sumOf { it.sms.amount },
                count    = transactions.size
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            Text(
                "SPENDING BY PAYMENT METHOD",
                color = LocalAppColors.current.secondaryText, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        if (byMethod.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) { Text("No transactions in this period.", color = LocalAppColors.current.secondaryText) }
            }
        } else {
            items(byMethod) { (method, txns) ->
                PaymentMethodCard(
                    method     = method,
                    amount     = txns.sumOf { it.sms.amount },
                    count      = txns.size,
                    percentage = (txns.sumOf { it.sms.amount } / totalSpent).toFloat().coerceIn(0f, 1f),
                    onClick    = { onMethodClick(method) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ─── Payment Method card ──────────────────────────────────────────────────────
@Composable
fun PaymentMethodCard(
    method: PaymentMethod,
    amount: Double,
    count: Int,
    percentage: Float,
    onClick: () -> Unit
) {
    val color = paymentMethodColor(method)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(color.copy(alpha = 0.12f))
                        .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(method.icon, fontSize = 22.sp) }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(method.displayName, color = LocalAppColors.current.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$count transaction${if (count != 1) "s" else ""}",
                        color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtAmt(amount, LocalShowAmounts.current), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("%.0f%%".format(percentage * 100), color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                }

                Spacer(Modifier.width(8.dp))
                Text("›", color = LocalAppColors.current.secondaryText, fontSize = 22.sp)
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(LocalAppColors.current.cardSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentage).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(color)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN 4 — Payment Method Detail (transactions grouped by category)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun PaymentMethodDetailScreen(
    method: PaymentMethod,
    transactions: List<ParsedWithCategory>,
    onBack: () -> Unit,
    onCategoryChange: (ParsedWithCategory, Category, SubCategory?, String?, Boolean, String?) -> Unit
) {
    val color      = paymentMethodColor(method)
    val debitTotal = transactions.filter { it.sms.type == TxType.DEBIT }.sumOf { it.sms.amount }

    val grouped = transactions
        .groupBy { it.category }
        .entries
        .map { (cat, txns) -> cat to txns.sortedByDescending { it.date } }
        .sortedByDescending { (_, txns) -> txns.filter { it.sms.type == TxType.DEBIT }.sumOf { it.sms.amount } }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(LocalAppColors.current.cardSurface, LocalAppColors.current.appBg)))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(LocalAppColors.current.cardBorder).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp) }

                Spacer(Modifier.width(14.dp))

                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(color.copy(alpha = 0.14f))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(method.icon, fontSize = 24.sp) }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(method.displayName, color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${transactions.size} transaction${if (transactions.size != 1) "s" else ""}  •  ${fmtAmt(debitTotal, LocalShowAmounts.current)}",
                        color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                    )
                }
            }
        }

        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions found.", color = LocalAppColors.current.secondaryText)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            grouped.forEach { (category, txns) ->
                val catTotal = txns.filter { it.sms.type == TxType.DEBIT }.sumOf { it.sms.amount }
                item {
                    CategorySectionHeader(category = category, amount = catTotal, count = txns.size)
                }
                items(txns) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                        TransactionCard(
                            item = item,
                            showSubcategoryAsPrimary = true,
                            onCategoryChange = { newCat, newSubCat, keyword, isOneTime, upiId ->
                                onCategoryChange(item, newCat, newSubCat, keyword, isOneTime, upiId)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── Category section header (used in Payment Method detail) ──────────────────
@Composable
fun CategorySectionHeader(category: Category, amount: Double, count: Int) {
    val color = categoryColor(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(3.dp).height(28.dp)
                .clip(RoundedCornerShape(2.dp)).background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(categoryIcon(category), fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.displayName,
                color = LocalAppColors.current.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "$count transaction${if (count != 1) "s" else ""}",
                color = LocalAppColors.current.secondaryText, fontSize = 11.sp
            )
        }
        if (amount > 0)
            Text("₹%.0f".format(amount), color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
