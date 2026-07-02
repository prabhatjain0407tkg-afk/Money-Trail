package com.expensetracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import com.expensetracker.sms.model.Confidence
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
val LocalBurndownExcludedTxKeys     = compositionLocalOf { emptySet<String>() }
val LocalBurndownExcludeTx          = compositionLocalOf<(String, Boolean) -> Unit> { { _, _ -> } }
val LocalBudgetExcludedTxKeys       = compositionLocalOf { emptySet<String>() }
val LocalBudgetExcludeTx            = compositionLocalOf<(String, Boolean) -> Unit> { { _, _ -> } }

fun fmtAmt(amount: Double, show: Boolean, prefix: String = "₹"): String =
    if (show) "$prefix%.0f".format(amount) else "$prefix ••••"

fun fmtAmtShort(amount: Double): String = when {
    amount >= 100_000 -> "₹${"%.1f".format(amount / 100_000)}L"
    amount >= 1_000   -> "₹${"%.0f".format(amount / 1_000)}k"
    else              -> "₹${"%.0f".format(amount)}"
}

// ─── Navigation ───────────────────────────────────────────────────────────────
private sealed class Screen {
    data class CategoryDetail(
        val category: Category,
        val subCatFilter: String?  = null,
        val titleOverride: String? = null,
        val iconOverride: String?  = null
    ) : Screen()
    object IncomingOverview : Screen()
    object SpentOverview    : Screen()
    object CreditCards      : Screen()
    object Profile          : Screen()
    object ReferFriend      : Screen()
    object About            : Screen()
    object Search           : Screen()
    object Duplicates            : Screen()
    object UncategorizedReview   : Screen()
    object PaymentMethods        : Screen()
    object Subscriptions         : Screen()
    object EmiTracker            : Screen()
}

// Derived from paymentMethod + bank + accountTail — no data-model change needed
val ParsedWithCategory.creditCardId: String?
    get() = if (paymentMethod == PaymentMethod.CREDIT_CARD && sms.accountTail != null)
                "${sms.bank} XX${sms.accountTail}" else null

// ─── Period ───────────────────────────────────────────────────────────────────
enum class Period(val label: String) {
    TODAY("Today"), WEEK("This Week"), MONTH("This Month"), ALL("All Time"), CUSTOM("Custom")
}

// ─── Donut chart view mode ─────────────────────────────────────────────────────
enum class DonutView(val label: String) {
    CATEGORY("Category"), WEEKLY("Weekly"), ALL("All"), PAYMENT("Payment")
}

// ─── Bottom navigation ────────────────────────────────────────────────────────
enum class BottomTab { HOME, TRANSACTIONS, ANALYTICS, CREDIT_CARDS, MORE }

// ─── Income tags (user-assigned labels for credit transactions) ───────────────
enum class IncomeTag(val label: String, val emoji: String) {
    SALARY("Salary", "💼"),
    BUSINESS("Business", "🏢"),
    INVESTMENT("Investment", "📈"),
    CC_BENEFITS("CC Benefits", "💳"),
    LOAN("Loan & Borrowings", "🏦"),
    REFUND("Refunds", "↩"),
    FAMILY_GIFTS("Family & Gifts", "🎁"),
    GOVT_BENEFITS("Govt Benefits", "🏛"),
    SHARE_MARKET("Share Market", "📊"),
    SALE_OF_ASSETS("Sale of Assets", "🏷"),
    OTHERS("Others", "📌")
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
    var geminiPromoHashes        by remember { mutableStateOf(UserPrefsStore.loadGeminiPromo(context)) }
    var aiEnabled    by remember { mutableStateOf(UserPrefsStore.isAiEnabled(context)) }
    var aiApiKey     by remember { mutableStateOf(UserPrefsStore.loadApiKey(context)) }
    var payeeNames   by remember { mutableStateOf(UserPrefsStore.loadPayeeNames(context)) }
    var txNotes      by remember { mutableStateOf(UserPrefsStore.loadNotes(context)) }
    var budgets      by remember { mutableStateOf(UserPrefsStore.loadBudgets(context)) }
    var totalBudget  by remember { mutableStateOf(UserPrefsStore.loadTotalBudget(context)) }
    var incomeTags           by remember { mutableStateOf(UserPrefsStore.loadIncomeTags(context)) }
    var includedTransferKeys by remember { mutableStateOf(UserPrefsStore.loadIncludedTransfers(context)) }
    var emiPinnedKeys               by remember { mutableStateOf(UserPrefsStore.loadEmiPins(context)) }
    var budgetExcludedCategories    by remember { mutableStateOf(UserPrefsStore.loadBudgetExcludedCategories(context)) }
    var budgetExcludedTxKeys        by remember { mutableStateOf(UserPrefsStore.loadBudgetExcludedTxKeys(context)) }
    var burndownExcludedCategories  by remember { mutableStateOf(UserPrefsStore.loadBurndownExcludedCategories(context)) }
    var burndownExcludedTxKeys      by remember { mutableStateOf(UserPrefsStore.loadBurndownExcludedTxKeys(context)) }
    var driveEmail         by remember { mutableStateOf(DriveBackupManager.signedInEmail(context)) }
    var driveLastBackupAt  by remember { mutableStateOf(UserPrefsStore.loadLastBackupTime(context)) }
    var driveBackupRunning by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showBackupToast    by remember { mutableStateOf<String?>(null) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var isLoading          by remember { mutableStateOf(false) }
    var lastUpdatedMs      by remember { mutableStateOf(0L) }
    var hasNotifPermission by remember { mutableStateOf(SmsNotificationListener.isGranted(context)) }
    var selectedPeriod      by remember { mutableStateOf(Period.MONTH) }
    var customRangeStart    by remember { mutableStateOf<Long?>(null) }
    var customRangeEnd      by remember { mutableStateOf<Long?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableStateOf(BottomTab.HOME) }
    var screenStack  by remember { mutableStateOf<List<Screen>>(emptyList()) }
    val currentScreen = screenStack.lastOrNull()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveBackupManager.DRIVE_SCOPE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
            }.onSuccess { acct ->
                driveEmail = acct?.email
                if (acct != null) DriveBackupWorker.schedule(context)
            }
        }
    }

    // Shared suspend action — reads inbox, updates state, and saves cache.
    suspend fun doRefresh() {
        val fresh = withContext(Dispatchers.IO) { readAndParseInbox(context) }
        rawParsed     = fresh
        lastUpdatedMs = System.currentTimeMillis()
        withContext(Dispatchers.IO) { TransactionCache.save(context, fresh) }
    }

    // ── Initial load + ContentObserver + 15-min periodic refresh ──────────────
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect

        // Load cache instantly so the dashboard appears without any delay.
        // Spinner only shows on the very first launch when there is no cache yet.
        val cached = withContext(Dispatchers.IO) { TransactionCache.load(context) }
        if (cached.isNotEmpty()) {
            rawParsed = cached          // immediate display from cache
        } else {
            isLoading = true            // first launch: nothing to show yet
        }

        doRefresh()                     // fresh parse runs; replaces cache data silently
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

    // ── Gemini background spam validation for low-confidence SMS ─────────────
    // Runs after every rawParsed refresh. Skips SMS already validated.
    // Never exceeds 14 req/min or 1490 req/day (free tier caps).
    LaunchedEffect(rawParsed) {
        val key = aiApiKey ?: return@LaunchedEffect
        if (!aiEnabled || key.isBlank()) return@LaunchedEffect

        val alreadyValidated = withContext(Dispatchers.IO) {
            UserPrefsStore.loadGeminiValidated(context)
        }

        // Only LOW-confidence SMS that haven't been checked yet
        val toValidate = rawParsed
            .filter { (parsed, _) -> parsed.confidence == Confidence.LOW }
            .filter { (parsed, _) -> parsed.rawText.hashCode() !in alreadyValidated }
            .map    { (parsed, _) -> parsed.rawText.hashCode() to parsed.rawText }

        if (toValidate.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val promoHashes = GeminiService.validateSpam(key, context, toValidate)
            UserPrefsStore.saveGeminiValidated(context, toValidate.map { it.first }.toSet())
            UserPrefsStore.saveGeminiPromo(context, promoHashes)
        }

        // Refresh state so allTransactions re-derives with new promo exclusions
        geminiPromoHashes = withContext(Dispatchers.IO) {
            UserPrefsStore.loadGeminiPromo(context)
        }
    }

    // ── WorkManager: background 15-min check + daily Drive backup ────────────
    LaunchedEffect(Unit) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "expense_bg_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        )
        if (DriveBackupManager.isConnected(context)) {
            DriveBackupWorker.schedule(context)
        }
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
    val allTransactions = remember(rawParsed, merchantOverrides, keywordOverrides, iconTagOverrides, oneTimeOverrides, upiOverrides, voidedTransactions, geminiPromoHashes) {
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
                isVoided = txKey in voidedTransactions ||
                    // Gemini flagged as promo, but only if the user hasn't manually categorized it
                    (sms.rawText.hashCode() in geminiPromoHashes && !hasOneTime && !hasUpiRule),
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

    val onEmiPinToggle = { txKey: String, pinned: Boolean ->
        UserPrefsStore.saveEmiPin(context, txKey, pinned)
        emiPinnedKeys = UserPrefsStore.loadEmiPins(context)
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

    // ── Restore confirm dialog ────────────────────────────────────────────────
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore from Google Drive?") },
            text  = {
                Text("This will overwrite your local settings with the cloud backup. The app will restart automatically.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    coroutineScope.launch {
                        driveBackupRunning = true
                        runCatching {
                            val json = DriveBackupManager.download(context)
                            if (json != null) {
                                withContext(Dispatchers.IO) { UserPrefsStore.importFromJson(context, json) }
                                showBackupToast = "Restore complete — restarting…"
                                (context as? Activity)?.recreate()
                            } else {
                                showBackupToast = "No backup found on Drive"
                            }
                        }.onFailure { showBackupToast = "Restore failed" }
                        driveBackupRunning = false
                    }
                }) { Text("Restore", color = LocalAppColors.current.accentGold) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel", color = LocalAppColors.current.secondaryText)
                }
            },
            containerColor    = LocalAppColors.current.cardBg,
            titleContentColor = LocalAppColors.current.primaryText,
            textContentColor  = LocalAppColors.current.secondaryText
        )
    }

    // ── Backup toast ──────────────────────────────────────────────────────────
    LaunchedEffect(showBackupToast) {
        if (showBackupToast != null) {
            delay(3000)
            showBackupToast = null
        }
    }

    // ── Drawer ────────────────────────────────────────────────────────────────
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Open || screenStack.isNotEmpty() || selectedBottomTab != BottomTab.HOME) {
        when {
            drawerState.currentValue == DrawerValue.Open -> coroutineScope.launch { drawerState.close() }
            screenStack.isNotEmpty() -> screenStack = screenStack.dropLast(1)
            else -> selectedBottomTab = BottomTab.HOME
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
        },
        LocalBurndownExcludedTxKeys provides burndownExcludedTxKeys,
        LocalBurndownExcludeTx provides { txKey, excluded ->
            UserPrefsStore.saveBurndownExcludedTx(context, txKey, excluded)
            burndownExcludedTxKeys = UserPrefsStore.loadBurndownExcludedTxKeys(context)
        },
        LocalBudgetExcludedTxKeys provides budgetExcludedTxKeys,
        LocalBudgetExcludeTx provides { txKey, excluded ->
            UserPrefsStore.saveBudgetExcludedTx(context, txKey, excluded)
            budgetExcludedTxKeys = UserPrefsStore.loadBudgetExcludedTxKeys(context)
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
                    onClose          = { coroutineScope.launch { drawerState.close() } },
                    driveEmail       = driveEmail,
                    driveLastBackupAt = driveLastBackupAt,
                    driveBackupRunning = driveBackupRunning,
                    onDriveSignIn    = { driveSignInLauncher.launch(googleSignInClient.signInIntent) },
                    onDriveSignOut   = {
                        googleSignInClient.signOut().addOnCompleteListener { driveEmail = null }
                    },
                    onDriveBackupNow = {
                        coroutineScope.launch {
                            driveBackupRunning = true
                            runCatching {
                                val json = withContext(Dispatchers.IO) {
                                    UserPrefsStore.exportAllToJson(context)
                                }
                                val ok = DriveBackupManager.upload(context, json)
                                if (ok) {
                                    driveLastBackupAt = System.currentTimeMillis()
                                    UserPrefsStore.saveLastBackupTime(context, driveLastBackupAt)
                                    showBackupToast = "Backup complete"
                                } else {
                                    showBackupToast = "Backup failed — check connection"
                                }
                            }.onFailure { showBackupToast = "Backup failed" }
                            driveBackupRunning = false
                        }
                    },
                    onDriveRestore   = { showRestoreConfirm = true }
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
            duplicateTxKeys  = duplicateTxKeys,
            emiPinnedKeys    = emiPinnedKeys,
            onEmiPinToggle   = onEmiPinToggle
        )
        Screen.IncomingOverview -> IncomingScreen(
            transactions         = transactions,
            incomeTags           = incomeTags,
            includedTransferKeys = includedTransferKeys,
            onTagChange          = { txKey, tag ->
                UserPrefsStore.saveIncomeTag(context, txKey, tag)
                incomeTags = UserPrefsStore.loadIncomeTags(context)
            },
            onTransferToggle     = { txKey, include ->
                UserPrefsStore.saveIncludedTransfer(context, txKey, include)
                includedTransferKeys = UserPrefsStore.loadIncludedTransfers(context)
            },
            onBack               = { screenStack = screenStack.dropLast(1) }
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
            },
            selectedPeriod   = selectedPeriod,
            customRangeStart = customRangeStart,
            customRangeEnd   = customRangeEnd,
            onPeriodChange   = { selectedPeriod = it },
            onShowDatePicker = { showDateRangePicker = true }
        )
        Screen.CreditCards -> PaymentMethodsScreen(
            transactions     = transactions,
            onBack           = { screenStack = screenStack.dropLast(1) },
            selectedPeriod   = selectedPeriod,
            customRangeStart = customRangeStart,
            customRangeEnd   = customRangeEnd,
            onPeriodChange   = { selectedPeriod = it },
            onShowDatePicker = { showDateRangePicker = true }
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
        Screen.UncategorizedReview -> UncategorizedReviewScreen(
            transactions     = allTransactions.filter { it.category == Category.UNCATEGORIZED && !it.isVoided },
            onBack           = { screenStack = screenStack.dropLast(1) },
            onCategoryChange = onCategoryChange
        )
        Screen.PaymentMethods -> PaymentMethodsScreen(
            transactions     = transactions,
            onBack           = { screenStack = screenStack.dropLast(1) },
            selectedPeriod   = selectedPeriod,
            onPeriodChange   = { selectedPeriod = it },
            onShowDatePicker = { showDateRangePicker = true },
            customRangeStart = customRangeStart,
            customRangeEnd   = customRangeEnd
        )
        Screen.Subscriptions -> SubscriptionsScreen(
            allTransactions = allTransactions,
            recurringKeys   = recurringKeys,
            onBack          = { screenStack = screenStack.dropLast(1) }
        )
        Screen.EmiTracker -> EmiTrackerScreen(
            allTransactions = allTransactions,
            emiPinnedKeys   = emiPinnedKeys,
            onBack          = { screenStack = screenStack.dropLast(1) }
        )
        null -> {
            val firstName = remember(profileName) { profileName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "there" }
            val syncLabel = remember(lastUpdatedMs) {
                if (lastUpdatedMs == 0L) "Never synced"
                else "Today, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastUpdatedMs))
            }
            Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    when (selectedBottomTab) {
                        BottomTab.HOME -> Column(modifier = Modifier.fillMaxSize()) {
                            // ── Header ───────────────────────────────────────
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hi, $firstName 👋",
                                        color = LocalAppColors.current.primaryText,
                                        fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(CreditColor))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Last synced: $syncLabel",
                                            color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                        .background(LocalAppColors.current.cardBg)
                                        .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                                        .clickable { screenStack = screenStack + Screen.Search },
                                        contentAlignment = Alignment.Center) { Text("🔍", fontSize = 16.sp) }
                                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (showAmounts) LocalAppColors.current.cardBg else LocalAppColors.current.accentGold.copy(alpha = 0.15f))
                                        .border(1.dp, if (showAmounts) LocalAppColors.current.cardBorder else LocalAppColors.current.accentGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .clickable { showAmounts = !showAmounts },
                                        contentAlignment = Alignment.Center) { Text(if (showAmounts) "👁" else "🙈", fontSize = 16.sp) }
                                    Box(modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(LocalAppColors.current.accentGold)
                                        .clickable { coroutineScope.launch { doRefresh() } },
                                        contentAlignment = Alignment.Center) {
                                        Text("↻", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            DashboardScreen(
                                hasPermission         = hasPermission,
                                isLoading             = isLoading,
                                transactions          = transactions,
                                allTransactions       = allTransactions,
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
                                onCcPaymentClick      = { screenStack = screenStack + Screen.CategoryDetail(Category.CC_PAYMENT) },
                                includedTransferKeys  = includedTransferKeys,
                                onRefresh             = { coroutineScope.launch { doRefresh() } },
                                onToggleAmounts       = { showAmounts = !showAmounts },
                                hasNotifPermission    = hasNotifPermission,
                                onEnableNotifListener = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
                                profileName           = profileName,
                                profileAvatarPath     = profileAvatarPath,
                                onOpenDrawer          = { coroutineScope.launch { drawerState.open() } },
                                onSearchClick         = { screenStack = screenStack + Screen.Search },
                                onDuplicatesClick     = { screenStack = screenStack + Screen.Duplicates },
                                onCategorizeClick     = { screenStack = screenStack + Screen.UncategorizedReview },
                                duplicateTxKeys       = duplicateTxKeys,
                                recurringKeys         = recurringKeys,
                                budgets               = budgets,
                                totalBudget           = totalBudget,
                                onTotalBudgetChange   = { newBudget ->
                                    totalBudget = newBudget
                                    UserPrefsStore.saveTotalBudget(context, newBudget)
                                },
                                onSubscriptionsClick         = { screenStack = screenStack + Screen.Subscriptions },
                                onEmiTrackerClick            = { screenStack = screenStack + Screen.EmiTracker },
                                emiPinnedKeys                = emiPinnedKeys,
                                budgetExcludedCategories     = budgetExcludedCategories,
                                onBudgetCategoryToggle       = { catName, excluded ->
                                    UserPrefsStore.saveBudgetExcludedCategory(context, catName, excluded)
                                    budgetExcludedCategories = UserPrefsStore.loadBudgetExcludedCategories(context)
                                },
                                burndownExcludedCategories   = burndownExcludedCategories,
                                onBurndownCategoryToggle     = { catName, excluded ->
                                    UserPrefsStore.saveBurndownExcludedCategory(context, catName, excluded)
                                    burndownExcludedCategories = UserPrefsStore.loadBurndownExcludedCategories(context)
                                }
                            )
                        }
                        BottomTab.TRANSACTIONS -> SpentScreen(
                            transactions          = transactions,
                            onBack                = { selectedBottomTab = BottomTab.HOME },
                            onCategoryClick       = { screenStack = screenStack + Screen.CategoryDetail(it) },
                            onCustomCategoryClick = { sub ->
                                screenStack = screenStack + Screen.CategoryDetail(
                                    category = Category.CUSTOM, subCatFilter = sub.id,
                                    titleOverride = sub.displayName, iconOverride = sub.icon
                                )
                            },
                            selectedPeriod   = selectedPeriod,
                            customRangeStart = customRangeStart,
                            customRangeEnd   = customRangeEnd,
                            onPeriodChange   = { selectedPeriod = it },
                            onShowDatePicker = { showDateRangePicker = true }
                        )
                        BottomTab.ANALYTICS -> AnalyticsTabContent(
                            transactions     = transactions,
                            allTransactions  = allTransactions,
                            selectedPeriod   = selectedPeriod,
                            customRangeStart = customRangeStart,
                            customRangeEnd   = customRangeEnd,
                            onPeriodChange   = { selectedPeriod = it },
                            onShowDatePicker = { showDateRangePicker = true }
                        )
                        BottomTab.CREDIT_CARDS -> PaymentMethodsScreen(
                            transactions     = transactions,
                            onBack           = { selectedBottomTab = BottomTab.HOME },
                            selectedPeriod   = selectedPeriod,
                            customRangeStart = customRangeStart,
                            customRangeEnd   = customRangeEnd,
                            onPeriodChange   = { selectedPeriod = it },
                            onShowDatePicker = { showDateRangePicker = true }
                        )
                        BottomTab.MORE -> {}
                    }
                    if (selectedBottomTab == BottomTab.HOME) {
                        FloatingActionButton(
                            onClick        = { showCreateCategoryDialog = true },
                            modifier       = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                            containerColor = LocalAppColors.current.accentGold,
                            contentColor   = Color.Black,
                            shape          = RoundedCornerShape(16.dp)
                        ) {
                            Text("＋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                    // Backup status toast
                    if (showBackupToast != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 80.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(LocalAppColors.current.cardSurface)
                                .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(showBackupToast ?: "", color = LocalAppColors.current.primaryText, fontSize = 13.sp)
                        }
                    }
                } // Box
                BottomNavBar(selectedBottomTab) { tab ->
                    if (tab == BottomTab.MORE) coroutineScope.launch { drawerState.open() }
                    else selectedBottomTab = tab
                }
            } // Column
        }
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
    allTransactions: List<ParsedWithCategory>,
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
    onCcPaymentClick: () -> Unit = {},
    includedTransferKeys: Set<String> = emptySet(),
    onRefresh: () -> Unit,
    onToggleAmounts: () -> Unit = {},
    hasNotifPermission: Boolean,
    onEnableNotifListener: () -> Unit,
    profileName: String = "",
    profileAvatarPath: String? = null,
    onOpenDrawer: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onDuplicatesClick: () -> Unit = {},
    onCategorizeClick: () -> Unit = {},
    duplicateTxKeys: Set<String> = emptySet(),
    recurringKeys: Set<String> = emptySet(),
    budgets: Map<Category, Double> = emptyMap(),
    totalBudget: Double = 0.0,
    onTotalBudgetChange: (Double) -> Unit = {},
    onSubscriptionsClick: () -> Unit = {},
    onEmiTrackerClick: () -> Unit = {},
    emiPinnedKeys: Set<String> = emptySet(),
    budgetExcludedCategories: Set<String> = emptySet(),
    onBudgetCategoryToggle: (String, Boolean) -> Unit = { _, _ -> },
    burndownExcludedCategories: Set<String> = emptySet(),
    onBurndownCategoryToggle: (String, Boolean) -> Unit = { _, _ -> }
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

        // ── Key calculations ─────────────────────────────────────────────────
        val debits  = transactions.filter { it.sms.type == TxType.DEBIT  && !it.isVoided }
        val credits = transactions.filter { it.sms.type == TxType.CREDIT && !it.isVoided }
        val spent    = debits.filter  { it.category != Category.TRANSFER }.sumOf { it.sms.amount }
        val budgetExcludedTxKeysLocal = LocalBudgetExcludedTxKeys.current
        val budgetSpent = debits.filter {
            it.category != Category.TRANSFER &&
            it.category.name !in budgetExcludedCategories &&
            it.txKey !in budgetExcludedTxKeysLocal
        }.sumOf { it.sms.amount }
        val burndownExcludedTxKeysLocal = LocalBurndownExcludedTxKeys.current
        val burndownDebits = debits.filter {
            it.category != Category.TRANSFER &&
            it.category.name !in burndownExcludedCategories &&
            it.txKey !in burndownExcludedTxKeysLocal
        }
        val burndownSpent = burndownDebits.sumOf { it.sms.amount }
        val burndownCategories = remember(debits) {
            debits.filter { it.category != Category.TRANSFER }
                .map { it.category }.distinct().sortedBy { it.displayName }
        }
        val received = credits.filter {
            it.category != Category.CC_PAYMENT &&
            (it.category != Category.TRANSFER || it.txKey in includedTransferKeys)
        }.sumOf { it.sms.amount }
        val saved    = received - spent
        val spentPct = if (received > 0) (spent / received * 100).toInt().coerceIn(0, 100) else 0

        val ccBillTotal   = transactions.filter { it.category == Category.CC_PAYMENT && it.sms.type == TxType.CREDIT && !it.isVoided }.sumOf { it.sms.amount }
        val ccBillCount   = transactions.count  { it.category == Category.CC_PAYMENT && it.sms.type == TxType.CREDIT && !it.isVoided }
        val ccTransactions = transactions.filter { it.paymentMethod == PaymentMethod.CREDIT_CARD && it.sms.type == TxType.DEBIT && it.category != Category.TRANSFER && !it.isVoided }
        val ccTotal        = ccTransactions.sumOf { it.sms.amount }
        val ccCardCount    = ccTransactions.mapNotNull { it.creditCardId }.toSet().size

        // ── Last month comparison ─────────────────────────────────────────────
        val lastMonthSaved = remember(allTransactions) {
            val thisMonthStart = periodStartMs(Period.MONTH)
            val lmCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); add(Calendar.MONTH, -1) }
            val lmStart = lmCal.timeInMillis
            val lmTxns    = allTransactions.filter { it.date in lmStart until thisMonthStart && !it.isVoided }
            val lmSpent   = lmTxns.filter { it.sms.type == TxType.DEBIT  && it.category != Category.TRANSFER }.sumOf { it.sms.amount }
            val lmReceived= lmTxns.filter { it.sms.type == TxType.CREDIT && it.category != Category.TRANSFER && it.category != Category.CC_PAYMENT }.sumOf { it.sms.amount }
            lmReceived - lmSpent
        }
        val savingsDeltaPct = if (lastMonthSaved != 0.0) ((saved - lastMonthSaved) / Math.abs(lastMonthSaved) * 100).toInt() else 0

        // ── Review count ─────────────────────────────────────────────────────
        val dupeCount          = transactions.count { it.txKey in duplicateTxKeys }
        val uncategorizedCount = transactions.count { it.category == Category.UNCATEGORIZED && !it.isVoided }
        val reviewCount        = dupeCount + uncategorizedCount

        // ── Top spending category ─────────────────────────────────────────────
        val topSpentEntry = debits
            .filter { it.category != Category.TRANSFER }
            .groupBy { it.category }
            .entries.maxByOrNull { (_, v) -> v.sumOf { it.sms.amount } }

        val prevMonthName = remember {
            SimpleDateFormat("MMM", Locale.getDefault()).format(
                Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time
            )
        }
        val daysLeftInMonth = remember {
            Calendar.getInstance().let { it.getActualMaximum(Calendar.DAY_OF_MONTH) - it.get(Calendar.DAY_OF_MONTH) }
        }
        val savingsDiffSubtitle = remember(saved, lastMonthSaved) {
            val diff = saved - lastMonthSaved
            val diffLine = if (diff >= 0) "${fmtAmtShort(diff)} more saved than $prevMonthName"
                           else "${fmtAmtShort(-diff)} less saved than $prevMonthName"
            val daysLine = "$daysLeftInMonth day${if (daysLeftInMonth != 1) "s" else ""} left this month"
            "$diffLine\n$daysLine"
        }

        val recentDebits = remember(transactions) {
            transactions.filter { it.sms.type == TxType.DEBIT && !it.isVoided }
                .sortedByDescending { it.date }.take(5)
        }
        val daysElapsed = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
        val daysInMonth = remember { Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH) }

        // ── Budget alert computation ──────────────────────────────────────────
        val categorySpendMap = remember(debits) {
            debits.filter { it.category != Category.TRANSFER }
                .groupBy { it.category }
                .mapValues { (_, txs) -> txs.sumOf { it.sms.amount } }
        }
        val budgetAlerts = remember(budgets, categorySpendMap) {
            budgets.entries.mapNotNull { (cat, budget) ->
                val sp = categorySpendMap[cat] ?: 0.0
                if (sp < budget * 0.8) null else Triple(cat, sp, budget)
            }.sortedByDescending { (_, sp, bud) -> sp / bud }
        }

        // ── EMI tracker summary for dashboard card ────────────────────────────
        val emiKeywordsLocal = listOf(
            // EMI explicit
            "loan emi", "emi debit", "emi debited", "emi paid", "emi towards",
            "loan installment", "loan instalment", "towards emi", "for emi", "your emi",
            "emi of rs", "emi of inr", "emi amount",
            // Loan types (specific enough to not collide with SIP/insurance)
            "home loan", "personal loan", "car loan", "auto loan",
            "vehicle loan", "gold loan", "education loan", "business loan",
            "two wheeler loan", "consumer loan", "loan repayment", "loan payment",
            // NBFCs / lenders (named lenders are unambiguous)
            "bajaj finserv", "bajaj finance", "capital first", "fullerton",
            "muthoot", "mahindra finance", "shriram finance", "tata capital",
            "hdfc credila", "aditya birla finance", "l&t finance", "piramal finance",
            "idfc first", "iifl finance", "manappuram", "cholamandalam",
        )
        val emiCategories = setOf(Category.BILLS, Category.TRANSFER, Category.UNCATEGORIZED)
        val totalMonthlyEmi = remember(allTransactions,  emiPinnedKeys) {
            allTransactions.filter { tx ->
                !tx.isVoided && tx.sms.type == TxType.DEBIT &&
                (tx.txKey in emiPinnedKeys ||
                 (tx.category in emiCategories &&
                  emiKeywordsLocal.any { kw -> tx.sms.rawText.lowercase().contains(kw) }))
            }.groupBy { tx ->
                tx.sms.merchant ?: tx.sms.bank ?: "Unknown"
            }.values.sumOf { txs -> txs.map { it.sms.amount }.average() }
        }

        // ── Subscriptions summary for dashboard card ──────────────────────────
        val totalMonthlySubscriptions = remember(allTransactions, recurringKeys) {
            recurringKeys.sumOf { key ->
                val matching = allTransactions.filter { tx ->
                    !tx.isVoided && tx.sms.type == TxType.DEBIT &&
                    (tx.category == Category.BILLS || tx.category == Category.ENTERTAINMENT ||
                     tx.category == Category.EDUCATION || tx.category == Category.HEALTH) &&
                    (tx.upiId?.lowercase() == key ||
                     tx.sms.merchant?.lowercase()?.trim() == key)
                }
                if (matching.isEmpty()) 0.0 else matching.take(3).map { it.sms.amount }.average()
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (!hasNotifPermission) {
                item { NotificationAccessBanner(onEnable = onEnableNotifListener); Spacer(Modifier.height(10.dp)) }
            }
            if (showRcsTip) {
                item {
                    RcsTipCard(
                        onDismiss = { prefs.edit().putBoolean("rcs_tip_dismissed", true).apply(); rcsTipDismissed = true },
                        onOpenSamsungMessages = { context.packageManager.getLaunchIntentForPackage("com.samsung.android.messaging")?.let { context.startActivity(it) } }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            // ── Hero card ────────────────────────────────────────────────────
            item {
                SavedThisMonthCard(
                    saved            = saved,
                    received         = received,
                    spent            = spent,
                    spentPct         = spentPct,
                    selectedPeriod   = selectedPeriod,
                    customRangeStart = customRangeStart,
                    customRangeEnd   = customRangeEnd,
                    onPeriodChange   = onPeriodChange,
                    onShowDatePicker = onShowDatePicker,
                    onIncomingClick  = onIncomingClick,
                    onSpentClick     = onSpentClick
                )
                Spacer(Modifier.height(12.dp))
            }
            // ── Burn rate strip (month period only) ─────────────────────────
            if (selectedPeriod == Period.MONTH && spent > 0) {
                item {
                    val burnRate  = burndownSpent / daysElapsed
                    val projected = burnRate * daysInMonth
                    BurnRateStrip(
                        burnRate                   = burnRate,
                        projectedSpend             = projected,
                        excludedFromBurndown       = burndownExcludedCategories,
                        presentCategories          = burndownCategories,
                        onCategoryExclusionToggle  = onBurndownCategoryToggle
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            // ── Total budget card ────────────────────────────────────────────
            if (selectedPeriod == Period.MONTH) {
                item {
                    TotalBudgetCard(
                        spent              = budgetSpent,
                        totalBudget        = totalBudget,
                        onBudgetSaved      = onTotalBudgetChange,
                        presentCategories  = burndownCategories,
                        excludedCategories = budgetExcludedCategories,
                        onExclusionChange  = onBudgetCategoryToggle
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            // ── Review banner ────────────────────────────────────────────────
            if (uncategorizedCount > 0 || dupeCount > 0) {
                item {
                    ReviewBanner(
                        uncategorizedCount = uncategorizedCount,
                        dupeCount          = dupeCount,
                        onCategorizeClick  = onCategorizeClick,
                        onDuplicatesClick  = onDuplicatesClick
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            // ── Budget alerts ────────────────────────────────────────────────
            if (budgetAlerts.isNotEmpty()) {
                item {
                    BudgetAlertsCard(
                        alerts          = budgetAlerts,
                        onCategoryClick = { /* already handled via CategoryDetailScreen */ }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            // ── Insights + Charts unified 2×2 grid ───────────────────────────
            item {
                val gridPad = PaddingValues(horizontal = 16.dp)
                val gap = 10.dp
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Text("INSIGHTS", color = LocalAppColors.current.secondaryText, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    // Row 1 — insight cards
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(gridPad),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        InsightCard(
                            modifier = Modifier.weight(1f),
                            icon = if (savingsDeltaPct >= 0) "📈" else "📉",
                            title = "Savings vs $prevMonthName",
                            highlight = "${if (savingsDeltaPct >= 0) "+" else ""}$savingsDeltaPct%",
                            highlightColor = if (savingsDeltaPct >= 0) CreditColor else DebitColor,
                            subtitle = savingsDiffSubtitle
                        )
                        InsightCard(
                            modifier = Modifier.weight(1f),
                            icon = if (topSpentEntry != null) categoryIcon(topSpentEntry.key) else "❓",
                            title = "Top Spending",
                            highlight = topSpentEntry?.key?.displayName ?: "None",
                            highlightColor = DebitColor,
                            subtitle = if (topSpentEntry != null) "${fmtAmt(topSpentEntry.value.sumOf { it.sms.amount }, LocalShowAmounts.current)}  •  ${if (spent > 0) (topSpentEntry.value.sumOf { it.sms.amount } / spent * 100).toInt() else 0}% of spend" else "No spending yet"
                        )
                    }
                    // Row 2 — chart cards
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(gridPad),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        SpendBarChartCard(allTransactions = allTransactions, modifier = Modifier.weight(1f))
                        CategoryDonutCard(
                            debits   = debits.filter { it.category != Category.TRANSFER },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // ── EMI Tracker + Subscriptions entry cards ──────────────────────
            item {
                val show = LocalShowAmounts.current
                val colors = LocalAppColors.current
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // EMI Tracker
                    Row(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.cardBg)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .clickable { onEmiTrackerClick() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏦", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("EMI Tracker", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (totalMonthlyEmi > 0) fmtAmt(totalMonthlyEmi, show) + "/mo" else "No EMIs",
                                color = if (totalMonthlyEmi > 0) DebitColor else colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                    // Subscriptions
                    Row(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.cardBg)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .clickable { onSubscriptionsClick() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📺", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Subscriptions", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (totalMonthlySubscriptions > 0) fmtAmt(totalMonthlySubscriptions, show) + "/mo" else "None",
                                color = if (totalMonthlySubscriptions > 0) DebitColor else colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // ── Recent transactions card (muted, bottom of feed) ─────────────
            if (recentDebits.isNotEmpty()) {
                item {
                    RecentTransactionsCard(transactions = recentDebits)
                    Spacer(Modifier.height(12.dp))
                }
            }
            // ── CC bill payments (only if present) ───────────────────────────
            if (ccBillTotal > 0) {
                item {
                    CcBillPaymentRow(total = ccBillTotal, count = ccBillCount, onClick = onCcPaymentClick)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ─── Bottom navigation bar ───────────────────────────────────────────────────
@Composable
fun BottomNavBar(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    val colors = LocalAppColors.current
    val tabs = listOf(
        BottomTab.HOME         to ("🏠" to "Home"),
        BottomTab.TRANSACTIONS to ("📋" to "Transactions"),
        BottomTab.ANALYTICS    to ("📊" to "Analytics"),
        BottomTab.CREDIT_CARDS to ("💳" to "Payment Modes"),
        BottomTab.MORE         to ("⚙️" to "Settings")
    )
    HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.cardBg).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEach { (tab, pair) ->
            val (icon, label) = pair
            val isSel = tab == selected
            Column(
                modifier = Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                Text(label, color = if (isSel) colors.accentGold else colors.secondaryText,
                    fontSize = 9.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, textAlign = TextAlign.Center)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(20.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                    .background(if (isSel) colors.accentGold else Color.Transparent))
            }
        }
    }
}

// ─── Hero: Saved This Month card ─────────────────────────────────────────────
@Composable
fun SavedThisMonthCard(
    saved: Double,
    received: Double,
    spent: Double,
    spentPct: Int,
    selectedPeriod: Period,
    customRangeStart: Long?,
    customRangeEnd: Long?,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit,
    onIncomingClick: () -> Unit,
    onSpentClick: () -> Unit
) {
    val show   = LocalShowAmounts.current
    val colors = LocalAppColors.current
    var periodMenuOpen by remember { mutableStateOf(false) }
    val periodLabel = when {
        selectedPeriod == Period.CUSTOM && customRangeStart != null -> {
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            "${fmt.format(Date(customRangeStart))} – ${fmt.format(Date(customRangeEnd ?: customRangeStart))}"
        }
        selectedPeriod == Period.MONTH -> "THIS MONTH"
        selectedPeriod == Period.TODAY -> "TODAY"
        selectedPeriod == Period.WEEK  -> "THIS WEEK"
        else -> selectedPeriod.label.uppercase()
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp)).background(colors.cardBg)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(24.dp)).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Box {
                    Row(modifier = Modifier.clickable { periodMenuOpen = true },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("SAVED $periodLabel", color = CreditColor, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("▾", color = CreditColor, fontSize = 9.sp)
                    }
                    DropdownMenu(expanded = periodMenuOpen, onDismissRequest = { periodMenuOpen = false },
                        containerColor = colors.cardSurface) {
                        Period.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label, color = if (p == selectedPeriod) colors.accentGold else colors.primaryText,
                                    fontWeight = if (p == selectedPeriod) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) },
                                onClick = { periodMenuOpen = false; if (p == Period.CUSTOM) onShowDatePicker() else onPeriodChange(p) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(fmtAmt(saved, show), color = colors.primaryText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
            // Circular ring: % of income spent — animates on open and on sync
            val ringAnim = remember { Animatable(0f) }
            LaunchedEffect(spentPct) {
                ringAnim.snapTo(0f)
                ringAnim.animateTo(
                    targetValue = spentPct / 100f,
                    animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
                )
            }
            val animatedPct = (ringAnim.value * 100).toInt()
            val arcColor = if (spentPct > 80) DebitColor else CreditColor
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                Canvas(modifier = Modifier.size(90.dp)) {
                    val sw = 10.dp.toPx()
                    drawArc(color = colors.cardSurface, startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round))
                    val sweep = ringAnim.value * 360f
                    if (sweep > 0f)
                        drawArc(color = arcColor, startAngle = -90f, sweepAngle = sweep,
                            useCenter = false, style = Stroke(width = sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$animatedPct%", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("of income", color = colors.secondaryText, fontSize = 8.sp)
                    Text("spent", color = colors.secondaryText, fontSize = 8.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
        Spacer(Modifier.height(14.dp))
        // INCOME | EXPENSE | NET SAVINGS
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).clickable { onIncomingClick() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(CreditColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) { Text("↓", color = CreditColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("INCOME", color = colors.secondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(fmtAmt(received, show), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).clickable { onSpentClick() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(DebitColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) { Text("↑", color = DebitColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("EXPENSE", color = colors.secondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(fmtAmt(spent, show), color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(colors.accentGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) { Text("💰", fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("NET SAVINGS", color = colors.secondaryText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(fmtAmt(saved, show), color = if (saved >= 0) CreditColor else DebitColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Review banner ────────────────────────────────────────────────────────────
@Composable
fun ReviewBanner(
    uncategorizedCount: Int,
    dupeCount: Int,
    onCategorizeClick: () -> Unit,
    onDuplicatesClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accentGold.copy(alpha = 0.10f))
            .border(1.dp, colors.accentGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        if (uncategorizedCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCategorizeClick() }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏷️", fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("$uncategorizedCount uncategorized",
                        color = colors.accentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap to categorize now",
                        color = colors.accentGold.copy(alpha = 0.65f), fontSize = 11.sp)
                }
                Text("›", color = colors.accentGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (uncategorizedCount > 0 && dupeCount > 0) {
            HorizontalDivider(color = colors.accentGold.copy(alpha = 0.20f), thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (dupeCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onDuplicatesClick() }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠️", fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Text("$dupeCount possible duplicate${if (dupeCount != 1) "s" else ""}",
                    color = colors.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                Text("›", color = colors.secondaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Total budget card ────────────────────────────────────────────────────────
@Composable
fun TotalBudgetCard(
    spent: Double,
    totalBudget: Double,
    onBudgetSaved: (Double) -> Unit,
    presentCategories: List<Category> = emptyList(),
    excludedCategories: Set<String> = emptySet(),
    onExclusionChange: (String, Boolean) -> Unit = { _, _ -> }
) {
    val colors  = LocalAppColors.current
    val show    = LocalShowAmounts.current
    var showDialog by remember { mutableStateOf(false) }
    var inputText  by remember { mutableStateOf(if (totalBudget > 0) totalBudget.toLong().toString() else "") }

    val isSet    = totalBudget > 0
    val pct      = if (isSet) (spent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f
    val pctInt   = (pct * 100).toInt()
    val barColor = when {
        pct >= 1f    -> Color(0xFFE53935)
        pct >= 0.9f  -> Color(0xFFE53935)
        pct >= 0.75f -> Aureate
        else         -> CreditColor
    }
    val statusMsg = when {
        !isSet       -> "Tap to set your monthly budget"
        pct >= 1f    -> "Over budget by ${fmtAmt(spent - totalBudget, show)}"
        pct >= 0.9f  -> "Almost at limit — ${fmtAmt(totalBudget - spent, show)} remaining"
        pct >= 0.75f -> "${((1f - pct) * 100).toInt()}% remaining of monthly budget"
        else         -> "${fmtAmt(totalBudget - spent, show)} remaining this month"
    }
    val exclusionCount = excludedCategories.intersect(presentCategories.map { it.name }.toSet()).size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBg)
            .border(
                1.dp,
                if (pct >= 0.9f) barColor.copy(alpha = 0.4f) else colors.cardBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable { showDialog = true }
            .padding(16.dp)
    ) {
        // ── Top row ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Monthly Budget", color = colors.primaryText,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (isSet) {
                Text("$pctInt% spent", color = barColor,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("+ Set budget", color = colors.secondaryText, fontSize = 12.sp)
            }
        }

        if (isSet) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtAmt(spent, show), color = barColor,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("of ${fmtAmt(totalBudget, show)}", color = colors.secondaryText, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(colors.cardSurface)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct)
                    .clip(RoundedCornerShape(4.dp)).background(barColor))
            }
            Spacer(Modifier.height(6.dp))
        }

        Text(statusMsg, color = colors.secondaryText, fontSize = 11.sp)

        // ── Exclusion badge ───────────────────────────────────────────────────
        if (exclusionCount > 0) {
            Spacer(Modifier.height(6.dp))
            val names = presentCategories
                .filter { it.name in excludedCategories }
                .joinToString(" · ") { it.displayName }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⊘ ", fontSize = 10.sp, color = colors.secondaryText)
                Text(
                    "Excludes: $names",
                    color = colors.secondaryText, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // ── Edit budget dialog ────────────────────────────────────────────────────
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor   = colors.cardBg,
            title = {
                Text("Monthly Budget", color = colors.primaryText,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── Amount field ─────────────────────────────────────────
                    Text("Spending limit (₹)", color = colors.secondaryText, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = { inputText = it.filter { c -> c.isDigit() } },
                        placeholder   = { Text("e.g. 50000", color = colors.secondaryText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = CreditColor,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedTextColor     = colors.primaryText,
                            unfocusedTextColor   = colors.primaryText,
                        )
                    )

                    // ── Category exclusions ───────────────────────────────────
                    if (presentCategories.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("EXCLUDE FROM BUDGET", color = colors.secondaryText, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("These categories won't count toward your limit — useful for EMI, investments, FD, etc.",
                            color = colors.secondaryText.copy(alpha = 0.7f), fontSize = 11.sp,
                            lineHeight = 15.sp)
                        Spacer(Modifier.height(2.dp))

                        // Skip TRANSFER (already excluded from budget spend)
                        val budgetCategories = presentCategories.filter { it != Category.TRANSFER }
                        budgetCategories.forEach { cat ->
                            val isExcluded = cat.name in excludedCategories
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isExcluded) colors.cardSurface
                                        else Color.Transparent
                                    )
                                    .clickable { onExclusionChange(cat.name, !isExcluded) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(categoryIcon(cat), fontSize = 16.sp, modifier = Modifier.width(28.dp))
                                Text(cat.displayName, color = if (isExcluded) colors.secondaryText else colors.primaryText,
                                    fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (isExcluded) {
                                    Text("⊘ Excluded", color = DebitColor.copy(alpha = 0.8f),
                                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Text("Include", color = colors.secondaryText.copy(alpha = 0.5f),
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = inputText.toDoubleOrNull() ?: 0.0
                    onBudgetSaved(v)
                    showDialog = false
                }) {
                    Text("Save", color = CreditColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (totalBudget > 0) {
                    TextButton(onClick = {
                        onBudgetSaved(0.0)
                        inputText = ""
                        showDialog = false
                    }) {
                        Text("Remove", color = DebitColor)
                    }
                } else {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel", color = colors.secondaryText)
                    }
                }
            }
        )
    }
}

@Composable
fun BurnRateStrip(
    burnRate: Double,
    projectedSpend: Double,
    excludedFromBurndown: Set<String> = emptySet(),
    presentCategories: List<Category> = emptyList(),
    onCategoryExclusionToggle: (String, Boolean) -> Unit = { _, _ -> }
) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current
    var expanded by remember { mutableStateOf(false) }
    val excludedCount = presentCategories.count { it.name in excludedFromBurndown }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardBg)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(14.dp))
    ) {
        // ── Summary row ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { if (presentCategories.isNotEmpty()) expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔥", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily burn rate", color = colors.secondaryText, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(fmtAmt(burnRate, show) + "/day",
                    color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (excludedCount > 0) {
                    Text("$excludedCount categor${if (excludedCount == 1) "y" else "ies"} excluded",
                        color = colors.secondaryText, fontSize = 10.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Month projection", color = colors.secondaryText, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(fmtAmtShort(projectedSpend),
                    color = DebitColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (presentCategories.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text(if (expanded) "▲" else "▼",
                    color = colors.secondaryText, fontSize = 12.sp)
            }
        }

        // ── Expandable category exclusion panel ───────────────────────────────
        if (expanded && presentCategories.isNotEmpty()) {
            HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("EXCLUDE FROM BURNDOWN", color = colors.secondaryText, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Excluded categories won't affect daily rate or projection",
                    color = colors.secondaryText.copy(alpha = 0.6f), fontSize = 10.sp)
                Spacer(Modifier.height(10.dp))
                presentCategories.forEach { cat ->
                    val isExcluded = cat.name in excludedFromBurndown
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCategoryExclusionToggle(cat.name, !isExcluded) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(categoryIcon(cat), fontSize = 18.sp, modifier = Modifier.width(32.dp))
                        Text(cat.displayName,
                            color = if (isExcluded) colors.secondaryText else colors.primaryText,
                            fontSize = 13.sp, modifier = Modifier.weight(1f),
                            fontWeight = if (isExcluded) FontWeight.Normal else FontWeight.Medium)
                        Box(
                            modifier = Modifier.size(22.dp, 14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (!isExcluded) DebitColor else colors.cardSurface)
                                .border(1.dp,
                                    if (!isExcluded) DebitColor else colors.cardBorder,
                                    RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isExcluded)
                                Box(Modifier.size(10.dp, 10.dp).clip(RoundedCornerShape(5.dp))
                                    .background(Color.White).align(Alignment.CenterEnd)
                                    .padding(end = 2.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (isExcluded) "Off" else "On",
                            color = if (isExcluded) colors.secondaryText else DebitColor,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(24.dp))
                    }
                }
            }
        }
    }
}

// ─── Recent transactions compact card (muted / semi-visible) ──────────────────
@Composable
fun RecentTransactionsCard(transactions: List<ParsedWithCategory>) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current
    val fmt    = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .alpha(0.65f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBg)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text("RECENT", color = colors.secondaryText, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        transactions.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(
                color = colors.cardBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(categoryIcon(item.category), fontSize = 16.sp)
                Text(
                    item.sms.merchant ?: item.upiId?.substringBefore("@") ?: item.sms.bank,
                    color = colors.secondaryText, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fmtAmt(item.sms.amount, show),
                    color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    fmt.format(Date(item.date)),
                    color = colors.secondaryText.copy(alpha = 0.6f), fontSize = 10.sp
                )
            }
        }
    }
}

// ─── Insights section ─────────────────────────────────────────────────────────
@Composable
fun InsightsSection(
    prevMonthName: String,
    savingsDeltaPct: Int,
    topCategory: Category?,
    topCategoryAmt: Double,
    topCategoryPct: Double
) {
    val show   = LocalShowAmounts.current
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("INSIGHTS", color = colors.secondaryText, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InsightCard(
                modifier = Modifier.weight(1f),
                icon = if (savingsDeltaPct >= 0) "📈" else "📉",
                title = "Savings vs $prevMonthName",
                highlight = "${if (savingsDeltaPct >= 0) "+" else ""}$savingsDeltaPct%",
                highlightColor = if (savingsDeltaPct >= 0) CreditColor else DebitColor,
                subtitle = if (savingsDeltaPct > 5) "Great job!" else if (savingsDeltaPct < -5) "Spend less this month" else "About the same"
            )
            InsightCard(
                modifier = Modifier.weight(1f),
                icon = if (topCategory != null) categoryIcon(topCategory) else "❓",
                title = "Top Spending",
                highlight = topCategory?.displayName ?: "None",
                highlightColor = DebitColor,
                subtitle = if (topCategory != null) "${fmtAmt(topCategoryAmt, show)}  •  ${topCategoryPct.toInt()}% of spend" else "No spending yet"
            )
        }
    }
}

@Composable
fun InsightCard(
    icon: String,
    title: String,
    highlight: String,
    highlightColor: Color,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
            .background(colors.cardBg).border(0.5.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp)
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(colors.cardSurface),
            contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) }
        Spacer(Modifier.height(12.dp))
        Text(title, color = colors.secondaryText, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(highlight, color = highlightColor, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = colors.secondaryText, fontSize = 10.sp,
            lineHeight = 14.sp, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Bar chart: monthly spending ─────────────────────────────────────────────
@Composable
fun SpendBarChartCard(allTransactions: List<ParsedWithCategory>, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val monthlySpend = remember(allTransactions) {
        (0 until 6).map { monthsAgo ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, -monthsAgo)
            }
            val startMs = cal.timeInMillis
            val endMs   = Calendar.getInstance().also { it.timeInMillis = startMs; it.add(Calendar.MONTH, 1) }.timeInMillis - 1
            val name    = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
            val spend   = allTransactions.filter { it.date in startMs..endMs && it.sms.type == TxType.DEBIT && it.category != Category.TRANSFER && !it.isVoided }.sumOf { it.sms.amount }
            name to spend
        }.reversed()
    }
    val maxSpend = monthlySpend.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val lastIdx  = monthlySpend.lastIndex
    val barColor = colors.accentGold
    val dimColor = colors.accentGold.copy(alpha = 0.30f)

    Column(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(colors.cardBg)
        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text("Monthly Spend", color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text("last 6 months", color = colors.secondaryText.copy(alpha = 0.6f), fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        // ₹ amount labels above each bar
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            monthlySpend.forEachIndexed { idx, (_, spend) ->
                Text(
                    if (spend > 0) fmtAmtShort(spend) else "–",
                    color = if (idx == lastIdx) colors.accentGold else colors.secondaryText.copy(alpha = 0.55f),
                    fontSize = 7.sp,
                    fontWeight = if (idx == lastIdx) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
            val gap      = 5.dp.toPx()
            val barW     = (size.width - gap * (monthlySpend.size - 1)) / monthlySpend.size
            monthlySpend.forEachIndexed { idx, (_, spend) ->
                val frac    = (spend / maxSpend).toFloat().coerceIn(0f, 1f)
                val barH    = frac * (size.height - 2.dp.toPx())
                val x       = idx * (barW + gap)
                val y       = size.height - barH
                drawRoundRect(
                    color        = if (idx == lastIdx) barColor else dimColor,
                    topLeft      = androidx.compose.ui.geometry.Offset(x, y),
                    size         = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            monthlySpend.forEachIndexed { idx, (name, _) ->
                Text(
                    name,
                    color = if (idx == lastIdx) colors.accentGold else colors.secondaryText,
                    fontSize = 8.sp,
                    fontWeight = if (idx == lastIdx) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── Donut chart: multi-view (category / weekly / all / payment) ─────────────
@Composable
fun CategoryDonutCard(
    debits: List<ParsedWithCategory>,
    modifier: Modifier = Modifier,
    showToggle: Boolean = false
) {
    val colors = LocalAppColors.current
    var donutView by remember { mutableStateOf(DonutView.CATEGORY) }

    val pieColors = listOf(
        Color(0xFF4B9E74), Color(0xFFD4A85C), Color(0xFFBF5B4C),
        Color(0xFF5B7EC4), Color(0xFFA05BA8), Color(0xFF4BBDB4),
        Color(0xFFD4845C), Color(0xFF8B5BC4), Color(0xFF9E7B4B),
        Color(0xFF5B8EBF)
    )

    val categoryData = remember(debits) {
        debits.groupBy { it.category }
            .entries.sortedByDescending { (_, v) -> v.sumOf { it.sms.amount } }.take(5)
            .map { (cat, txns) -> cat.displayName to txns.sumOf { it.sms.amount } }
    }
    val allCategoryData = remember(debits) {
        debits.groupBy { it.category }
            .entries.sortedByDescending { (_, v) -> v.sumOf { it.sms.amount } }
            .map { (cat, txns) -> cat.displayName to txns.sumOf { it.sms.amount } }
    }
    val weeklyData = remember(debits) {
        debits.groupBy { tx ->
            val day = Calendar.getInstance().also { it.timeInMillis = tx.date }.get(Calendar.DAY_OF_MONTH)
            "W${(day - 1) / 7 + 1}"
        }.entries.sortedBy { it.key }
            .map { (week, txns) -> week to txns.sumOf { it.sms.amount } }
    }
    val paymentData = remember(debits) {
        PaymentMethod.entries
            .map { m -> "${m.icon} ${m.displayName}" to debits.filter { it.paymentMethod == m }.sumOf { it.sms.amount } }
            .filter { (_, amt) -> amt > 0 }
            .sortedByDescending { (_, amt) -> amt }
    }

    val activeData = when (donutView) {
        DonutView.CATEGORY -> categoryData
        DonutView.WEEKLY   -> weeklyData
        DonutView.ALL      -> allCategoryData
        DonutView.PAYMENT  -> paymentData
    }
    val total = activeData.sumOf { it.second }

    val donutSize    = if (showToggle) 140.dp else 70.dp
    val strokeWidth  = if (showToggle) 20.dp  else 14.dp
    val legendCount  = if (showToggle) activeData.size else 3
    val textSize     = if (showToggle) 12.sp else 9.sp

    Column(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(colors.cardBg)
        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(20.dp)).padding(14.dp)) {

        // Header
        val title = when (donutView) {
            DonutView.CATEGORY -> "By Category"
            DonutView.WEEKLY   -> "Week-wise Spend"
            DonutView.ALL      -> "All Categories"
            DonutView.PAYMENT  -> "By Payment Method"
        }
        val subtitle = when (donutView) {
            DonutView.CATEGORY -> "top 5 categories"
            DonutView.WEEKLY   -> "spend per week in period"
            DonutView.ALL      -> "every category"
            DonutView.PAYMENT  -> "UPI, card, cash & more"
        }
        Text(title, color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(subtitle, color = colors.secondaryText.copy(alpha = 0.6f), fontSize = 9.sp)

        // View selector (Analytics tab only) — dropdown instead of a 4-wide button row
        if (showToggle) {
            Spacer(Modifier.height(10.dp))
            var viewMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.accentGold.copy(alpha = 0.12f))
                        .border(0.5.dp, colors.accentGold.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                        .clickable { viewMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(donutView.label, color = colors.accentGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("▾", color = colors.accentGold, fontSize = 9.sp)
                }
                DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false },
                    containerColor = colors.cardSurface) {
                    DonutView.entries.forEach { view ->
                        DropdownMenuItem(
                            text = {
                                Text(view.label,
                                    color = if (view == donutView) colors.accentGold else colors.primaryText,
                                    fontWeight = if (view == donutView) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp)
                            },
                            onClick = {
                                donutView = view
                                viewMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (total > 0) {
            Box(modifier = Modifier.size(donutSize).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    activeData.forEachIndexed { idx, (_, amount) ->
                        val sweep = (amount / total * 360f).toFloat()
                        drawArc(color = pieColors[idx % pieColors.size],
                            startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt))
                        startAngle += sweep
                    }
                }
                // Center label for weekly view
                if (donutView == DonutView.WEEKLY && showToggle) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${weeklyData.size}", color = colors.primaryText,
                            fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("weeks", color = colors.secondaryText, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            activeData.take(legendCount).forEachIndexed { idx, (label, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(pieColors[idx % pieColors.size]))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = colors.secondaryText, fontSize = textSize,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(amount / total * 100).toInt()}%",
                        color = colors.primaryText, fontSize = textSize, fontWeight = FontWeight.SemiBold)
                }
                if (idx < activeData.take(legendCount).lastIndex)
                    Spacer(Modifier.height(if (showToggle) 7.dp else 3.dp))
            }
        } else {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("No data", color = colors.secondaryText, fontSize = 12.sp)
            }
        }
    }
}

// ─── Analytics tab ────────────────────────────────────────────────────────────
@Composable
fun AnalyticsTabContent(
    transactions: List<ParsedWithCategory>,
    allTransactions: List<ParsedWithCategory>,
    selectedPeriod: Period,
    customRangeStart: Long?,
    customRangeEnd: Long?,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit
) {
    val colors = LocalAppColors.current
    val debits = transactions.filter { it.sms.type == TxType.DEBIT && !it.isVoided && it.category != Category.TRANSFER }
    Column(modifier = Modifier.fillMaxSize().background(colors.appBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Analytics", color = colors.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("spending patterns", color = colors.secondaryText, fontSize = 11.sp)
            }
            PeriodDropdownButton(selectedPeriod, customRangeStart, customRangeEnd, onPeriodChange, onShowDatePicker)
        }
        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SpendBarChartCard(allTransactions = allTransactions, modifier = Modifier.fillMaxWidth()) }
            item { CategoryDonutCard(debits = debits, modifier = Modifier.fillMaxWidth(), showToggle = true) }
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

// (Credit-card-only screen removed — replaced by the accordion-style Payment Modes
// screen further below, which covers Credit Card, UPI, Debit Card, and Net Banking.)

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
            Text("CC Bill Payments Received", color = Aureate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("$count confirmation${if (count > 1) "s" else ""}  •  excluded from income",
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
    duplicateTxKeys: Set<String> = emptySet(),
    emiPinnedKeys: Set<String> = emptySet(),
    onEmiPinToggle: (String, Boolean) -> Unit = { _, _ -> }
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
                            isEmiPinned              = item.txKey in emiPinnedKeys,
                            onEmiPinToggle           = { onEmiPinToggle(item.txKey, item.txKey !in emiPinnedKeys) },
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
fun TabPeriodRow(
    selectedPeriod: Period,
    customRangeStart: Long?,
    customRangeEnd: Long?,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 10.dp)
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
}

@Composable
fun PeriodDropdownButton(
    selectedPeriod: Period,
    customRangeStart: Long?,
    customRangeEnd: Long?,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit
) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        selectedPeriod == Period.CUSTOM && customRangeStart != null -> {
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            "${fmt.format(Date(customRangeStart))}–${fmt.format(Date(customRangeEnd ?: customRangeStart))}"
        }
        else -> selectedPeriod.label
    }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.accentGold.copy(alpha = 0.12f))
                .border(0.5.dp, colors.accentGold.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(label, color = colors.accentGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("▾", color = colors.accentGold, fontSize = 9.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = colors.cardSurface) {
            Period.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(p.label,
                            color = if (p == selectedPeriod) colors.accentGold else colors.primaryText,
                            fontWeight = if (p == selectedPeriod) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp)
                    },
                    onClick = {
                        expanded = false
                        if (p == Period.CUSTOM) onShowDatePicker() else onPeriodChange(p)
                    }
                )
            }
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
    onClose: () -> Unit,
    driveEmail: String? = null,
    driveLastBackupAt: Long = 0L,
    driveBackupRunning: Boolean = false,
    onDriveSignIn: () -> Unit = {},
    onDriveSignOut: () -> Unit = {},
    onDriveBackupNow: () -> Unit = {},
    onDriveRestore: () -> Unit = {}
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
            Triple("👤", "Profile",          Screen.Profile as Screen),
            Triple("💳", "Payment Modes",  Screen.PaymentMethods as Screen),
            Triple("🎁", "Refer a Friend",   Screen.ReferFriend as Screen),
            Triple("ℹ️", "About Us",         Screen.About as Screen)
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

        // ── Google Drive Backup ────────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Text("BACKUP", color = LocalAppColors.current.secondaryText, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(12.dp))
            if (driveEmail == null) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalAppColors.current.cardSurface)
                        .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(12.dp))
                        .clickable(enabled = !driveBackupRunning) { onDriveSignIn() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☁️", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sign in with Google", color = LocalAppColors.current.primaryText,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Enable daily backups to Google Drive",
                            color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                    }
                    Text("›", color = LocalAppColors.current.secondaryText, fontSize = 18.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("☁️", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(driveEmail, color = LocalAppColors.current.primaryText, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (driveLastBackupAt > 0) {
                            val fmt = remember(driveLastBackupAt) {
                                SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                                    .format(Date(driveLastBackupAt))
                            }
                            Text("Last: $fmt", color = CreditColor, fontSize = 10.sp)
                        } else {
                            Text("No backup yet", color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                        }
                    }
                    TextButton(onClick = onDriveSignOut) {
                        Text("Sign out", color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (driveBackupRunning) LocalAppColors.current.cardSurface
                                else LocalAppColors.current.accentGold.copy(alpha = 0.15f)
                            )
                            .border(1.dp,
                                LocalAppColors.current.accentGold.copy(alpha = if (driveBackupRunning) 0.3f else 0.6f),
                                RoundedCornerShape(10.dp))
                            .clickable(enabled = !driveBackupRunning) { onDriveBackupNow() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (driveBackupRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = LocalAppColors.current.accentGold)
                        } else {
                            Text("⬆  Back Up Now", color = LocalAppColors.current.accentGold,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalAppColors.current.cardSurface)
                            .border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                            .clickable(enabled = !driveBackupRunning) { onDriveRestore() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⬇  Restore", color = LocalAppColors.current.primaryText,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalAppColors.current.cardSurface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("🔒", fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Backed up daily at 12:30 AM · Private to your Google account",
                        color = LocalAppColors.current.secondaryText, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
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
    onCustomCategoryClick: (SubCategory) -> Unit,
    selectedPeriod: Period,
    customRangeStart: Long?,
    customRangeEnd: Long?,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit
) {
    val customCategories = LocalCustomCategories.current
    val debits = transactions.filter { it.sms.type == TxType.DEBIT && it.category != Category.TRANSFER }
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
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
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
            PeriodDropdownButton(selectedPeriod, customRangeStart, customRangeEnd, onPeriodChange, onShowDatePicker)
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
    incomeTags: Map<String, String>,
    includedTransferKeys: Set<String>,
    onTagChange: (String, String?) -> Unit,
    onTransferToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current

    val allDebits = remember(transactions) {
        transactions.filter { it.sms.type == TxType.DEBIT && !it.isVoided }
    }

    val allCredits = transactions.filter {
        it.sms.type == TxType.CREDIT && !it.isVoided && it.category != Category.CC_PAYMENT
    }.sortedByDescending { it.date }

    val totalReceived = allCredits.filter {
        it.category != Category.TRANSFER || it.txKey in includedTransferKeys
    }.sumOf { it.sms.amount }

    Column(modifier = Modifier.fillMaxSize().background(colors.appBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(colors.cardBg).border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("←", color = colors.primaryText, fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📥", fontSize = 18.sp); Spacer(Modifier.width(8.dp))
                    Text("Incoming", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text("${allCredits.size} transaction${if (allCredits.size != 1) "s" else ""}",
                    color = colors.secondaryText, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtAmt(totalReceived, show), color = CreditColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("total income", color = colors.secondaryText, fontSize = 10.sp)
            }
        }
        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)

        if (allCredits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No incoming transactions in this period.", color = colors.secondaryText)
            }
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            items(allCredits, key = { it.txKey }) { tx ->
                val isTransfer = tx.category == Category.TRANSFER
                val isIncluded = tx.txKey in includedTransferKeys
                val txIncomeTag = incomeTags[tx.txKey]
                    ?.let { runCatching { IncomeTag.valueOf(it) }.getOrNull() }

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TransactionCard(
                        item              = tx,
                        allDebits         = allDebits,
                        incomeTag         = if (!isTransfer) txIncomeTag else null,
                        onIncomeTagChange = if (!isTransfer) { tag ->
                            onTagChange(tx.txKey, tag?.name)
                        } else null,
                        onCategoryChange  = { _, _, _, _, _ -> }
                    )
                }

                // Self-transfer include/exclude chip (transfers don't use income tags)
                if (isTransfer) {
                    Box(
                        modifier = Modifier
                            .padding(start = 20.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isIncluded) CreditColor.copy(alpha = 0.12f) else colors.cardSurface)
                            .border(0.5.dp,
                                if (isIncluded) CreditColor.copy(alpha = 0.4f) else colors.cardBorder,
                                RoundedCornerShape(20.dp))
                            .clickable { onTransferToggle(tx.txKey, !isIncluded) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (isIncluded) "✓ Included in income" else "Tap to include in income",
                            color = if (isIncluded) CreditColor else colors.secondaryText,
                            fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                    }
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

// ─── One row inside the "Exclude options" dropdown ────────────────────────────
@Composable
private fun ExcludeOptionRow(
    emoji: String,
    label: String,
    sub: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalAppColors.current
    DropdownMenuItem(
        onClick = onToggle,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 15.sp, modifier = Modifier.width(26.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label,
                        color = if (checked) DebitColor else colors.primaryText,
                        fontSize = 13.sp,
                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                    Text(sub, color = colors.secondaryText, fontSize = 10.sp)
                }
                Spacer(Modifier.width(10.dp))
                // Checkbox-style indicator
                Box(
                    modifier = Modifier.size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (checked) DebitColor.copy(alpha = 0.18f) else colors.cardBg)
                        .border(1.dp,
                            if (checked) DebitColor.copy(alpha = 0.7f) else colors.cardBorder,
                            RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) Text("✓", color = DebitColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

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
    isEmiPinned: Boolean = false,
    onEmiPinToggle: (() -> Unit)? = null,
    incomeTag: IncomeTag? = null,
    onIncomeTagChange: ((IncomeTag?) -> Unit)? = null,
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
                val displayIcon = when {
                    incomeTag != null -> incomeTag.emoji
                    item.subCategory.icon != categoryIcon(item.category) -> item.subCategory.icon
                    else -> categoryIcon(item.category)
                }
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
                    val tagLabel = when {
                        incomeTag != null -> incomeTag.label
                        item.subCategory.id != item.category.name -> item.subCategory.displayName
                        else -> item.category.displayName
                    }
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
                    if (incomeTag != null) Text("${incomeTag.emoji} ${incomeTag.label}", color = CreditColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    if (incomeTag == null && onIncomeTagChange != null) Text("🏷 Tap to tag", color = LocalAppColors.current.secondaryText, fontSize = 9.sp)
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

                // ── Quick icon tag strip (hidden for income; income tag picker handles it) ──
                val iconOptions = SubCategoryRules.ICON_OPTIONS[item.category]
                if (!iconOptions.isNullOrEmpty() && onIncomeTagChange == null) {
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

                // ── Income tag picker (credit transactions with onIncomeTagChange) ──
                if (onIncomeTagChange != null) {
                    var incomeTagMenuOpen by remember { mutableStateOf(false) }
                    Text("INCOME TAG", color = LocalAppColors.current.secondaryText, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (incomeTag != null) CreditColor.copy(alpha = 0.10f)
                                    else LocalAppColors.current.cardSurface
                                )
                                .border(0.5.dp,
                                    if (incomeTag != null) CreditColor.copy(alpha = 0.45f)
                                    else LocalAppColors.current.cardBorder,
                                    RoundedCornerShape(20.dp))
                                .clickable { incomeTagMenuOpen = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (incomeTag != null) "${incomeTag.emoji} ${incomeTag.label}" else "🏷  Add tag",
                                color = if (incomeTag != null) CreditColor else LocalAppColors.current.secondaryText,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                            Text("▾", color = if (incomeTag != null) CreditColor else LocalAppColors.current.secondaryText, fontSize = 10.sp)
                        }
                        DropdownMenu(
                            expanded = incomeTagMenuOpen,
                            onDismissRequest = { incomeTagMenuOpen = false },
                            containerColor = LocalAppColors.current.cardSurface
                        ) {
                            if (incomeTag != null) {
                                DropdownMenuItem(
                                    text = { Text("✕  Remove tag", color = LocalAppColors.current.secondaryText, fontSize = 13.sp) },
                                    onClick = { onIncomeTagChange(null); incomeTagMenuOpen = false }
                                )
                                HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)
                            }
                            IncomeTag.entries.forEach { tag ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tag.emoji, fontSize = 16.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(tag.label,
                                                color = if (tag == incomeTag) CreditColor else LocalAppColors.current.primaryText,
                                                fontSize = 13.sp,
                                                fontWeight = if (tag == incomeTag) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    },
                                    onClick = { onIncomeTagChange(tag); incomeTagMenuOpen = false }
                                )
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

                // Row 3: Track as EMI toggle (DEBIT only)
                if (sms.type == TxType.DEBIT && onEmiPinToggle != null) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isEmiPinned) Color(0xFF1B3A2D)
                                else LocalAppColors.current.cardSurface
                            )
                            .border(
                                0.5.dp,
                                if (isEmiPinned) CreditColor.copy(alpha = 0.6f)
                                else LocalAppColors.current.cardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onEmiPinToggle() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isEmiPinned) "✓  Tracked in EMI Tracker" else "📌  Track in EMI Tracker",
                            color = if (isEmiPinned) CreditColor else LocalAppColors.current.primaryText,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
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

                // ── Exclude options dropdown ─────────────────────────────────
                val doVoid              = LocalVoidTransaction.current
                val doRestore           = LocalRestoreTransaction.current
                val burndownExcludedTxs = LocalBurndownExcludedTxKeys.current
                val doBurndownExclude   = LocalBurndownExcludeTx.current
                val isBurndownExcluded  = item.txKey in burndownExcludedTxs
                val budgetExcludedTxs   = LocalBudgetExcludedTxKeys.current
                val doBudgetExclude     = LocalBudgetExcludeTx.current
                val isBudgetExcluded    = item.txKey in budgetExcludedTxs
                val isDebitTx           = sms.type == TxType.DEBIT
                var excludeMenuOpen     by remember { mutableStateOf(false) }

                val activeExclusions = buildList {
                    if (item.isVoided) add("Calculations")
                    if (isDebitTx && isBurndownExcluded) add("Burndown")
                    if (isDebitTx && isBudgetExcluded)   add("Budget")
                }
                val anyExcluded = activeExclusions.isNotEmpty()

                Spacer(Modifier.height(8.dp))
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (anyExcluded) DebitColor.copy(alpha = 0.08f)
                                else LocalAppColors.current.cardSurface
                            )
                            .border(0.5.dp,
                                if (anyExcluded) DebitColor.copy(alpha = 0.4f)
                                else LocalAppColors.current.cardBorder,
                                RoundedCornerShape(10.dp))
                            .clickable { excludeMenuOpen = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (anyExcluded) "⊘" else "⚙️",
                            color = if (anyExcluded) DebitColor else LocalAppColors.current.secondaryText,
                            fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (anyExcluded) "Excluded — tap to manage" else "Exclude options",
                                color = if (anyExcluded) DebitColor else LocalAppColors.current.primaryText,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (anyExcluded) activeExclusions.joinToString(" · ")
                                else if (isDebitTx) "Calculations · Burndown · Budget"
                                else "Calculations",
                                color = LocalAppColors.current.secondaryText, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("▾", color = LocalAppColors.current.secondaryText, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = excludeMenuOpen,
                        onDismissRequest = { excludeMenuOpen = false },
                        containerColor = LocalAppColors.current.cardSurface
                    ) {
                        ExcludeOptionRow(
                            emoji   = "⊘",
                            label   = "Exclude from calculations",
                            sub     = "Removes from every total",
                            checked = item.isVoided,
                            onToggle = {
                                if (item.isVoided) doRestore(item.txKey) else doVoid(item.txKey)
                                excludeMenuOpen = false
                            }
                        )
                        if (isDebitTx) {
                            ExcludeOptionRow(
                                emoji   = "📉",
                                label   = "Exclude from daily burndown",
                                sub     = "Stays in totals, not in burn rate",
                                checked = isBurndownExcluded,
                                onToggle = {
                                    doBurndownExclude(item.txKey, !isBurndownExcluded)
                                    excludeMenuOpen = false
                                }
                            )
                            ExcludeOptionRow(
                                emoji   = "🎯",
                                label   = "Exclude from monthly budget",
                                sub     = "Won't count against your limit",
                                checked = isBudgetExcluded,
                                onToggle = {
                                    doBudgetExclude(item.txKey, !isBudgetExcluded)
                                    excludeMenuOpen = false
                                }
                            )
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
    // Dedup by body hash alone — bank SMS always embed amount + ref/date in body, so two genuinely
    // different transactions never produce identical text. Using body hash (not date+hash) prevents
    // OEM database duplicates where the same SMS row is stored twice with slightly different timestamps.
    val seen    = HashSet<Int>()

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
            if (!seen.add(body.hashCode())) continue   // skip duplicate rows (same body = same transaction)
            val parsed = SmsParser.parse(sender, body) ?: continue
            results.add(Pair(parsed, date))
        }
    }

    // Merge notification-captured messages (RCS/Chat from Samsung Messages, Google Messages, etc.)
    // `seen` already holds all body hashes from ContentResolver, so this deduplicates cross-source.
    for ((sender, body, date) in SmsNotificationListener.loadCaptured(context)) {
        if (!seen.add(body.hashCode())) continue
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
// SCREEN — Uncategorized Quick Review
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun UncategorizedReviewScreen(
    transactions: List<ParsedWithCategory>,
    onBack: () -> Unit,
    onCategoryChange: (ParsedWithCategory, Category, SubCategory?, String?, Boolean, String?) -> Unit
) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current
    var showAllCategoriesFor by remember { mutableStateOf<ParsedWithCategory?>(null) }
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    val quickCategories = listOf(
        Category.FOOD, Category.GROCERY, Category.COMMUTE, Category.SHOPPING,
        Category.BILLS, Category.INVESTMENT, Category.ENTERTAINMENT, Category.HEALTH,
        Category.TRAVEL, Category.INCOME, Category.CASH
    )

    fun applyCategory(item: ParsedWithCategory, cat: Category) {
        val useOneTime = item.upiId == null && item.sms.merchant == null
        onCategoryChange(item, cat, null, null, useOneTime, item.upiId)
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.appBg)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = colors.primaryText, fontSize = 20.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Categorize", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (transactions.isEmpty()) "All done!" else "${transactions.size} remaining",
                    color = colors.secondaryText, fontSize = 12.sp
                )
            }
        }
        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 52.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("All caught up!", color = colors.primaryText,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Every transaction is categorized",
                        color = colors.secondaryText, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.txKey }) { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.cardBg)
                            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(38.dp).clip(CircleShape)
                                    .background(colors.cardSurface),
                                contentAlignment = Alignment.Center
                            ) { Text("❓", fontSize = 17.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.sms.merchant ?: item.upiId ?: item.sms.bank,
                                    color = colors.primaryText,
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${dateFmt.format(Date(item.date))}  •  ${item.paymentMethod.displayName}",
                                    color = colors.secondaryText, fontSize = 11.sp
                                )
                            }
                            Text(
                                fmtAmt(item.sms.amount, show),
                                color = if (item.sms.type == TxType.DEBIT) DebitColor else CreditColor,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        // Quick category chips
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickCategories) { cat ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.cardSurface)
                                        .border(0.5.dp, colors.cardBorder, RoundedCornerShape(20.dp))
                                        .clickable { applyCategory(item, cat) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(categoryIcon(cat), fontSize = 12.sp)
                                    Text(
                                        cat.displayName.split(" ").first(),
                                        color = colors.primaryText, fontSize = 11.sp
                                    )
                                }
                            }
                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.accentGold.copy(alpha = 0.12f))
                                        .border(0.5.dp, colors.accentGold.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                                        .clickable { showAllCategoriesFor = item }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("All ›", color = colors.accentGold,
                                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full category picker dialog
    showAllCategoriesFor?.let { item ->
        AlertDialog(
            onDismissRequest = { showAllCategoriesFor = null },
            containerColor   = colors.cardBg,
            title = {
                Text("Choose category", color = colors.primaryText,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn {
                    items(Category.entries.filter { it != Category.UNCATEGORIZED && it != Category.CUSTOM }) { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    applyCategory(item, cat)
                                    showAllCategoriesFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(categoryIcon(cat), fontSize = 20.sp)
                            Text(cat.displayName, color = colors.primaryText, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllCategoriesFor = null }) {
                    Text("Cancel", color = colors.secondaryText)
                }
            }
        )
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

// ═══════════════════════════════════════════════════════════════════════════════
// Budget Alerts Card
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun BudgetAlertsCard(
    alerts: List<Triple<Category, Double, Double>>,  // (category, spent, budget)
    onCategoryClick: (Category) -> Unit
) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current
    if (alerts.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DebitColor.copy(alpha = 0.07f))
            .border(1.dp, DebitColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("⚠️  BUDGET ALERTS", color = DebitColor, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        alerts.forEach { (cat, spent, budget) ->
            val pct    = (spent / budget).coerceIn(0.0, 1.0)
            val isOver = spent > budget
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCategoryClick(cat) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(categoryIcon(cat), fontSize = 20.sp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cat.displayName, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${fmtAmt(spent, show)} / ${fmtAmt(budget, show)}",
                            color = if (isOver) DebitColor else colors.secondaryText, fontSize = 12.sp
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.cardBorder)) {
                        Box(Modifier.fillMaxWidth(pct.toFloat()).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isOver) DebitColor else Aureate))
                    }
                }
                Text(
                    if (isOver) "${((spent / budget - 1) * 100).toInt()}% over"
                    else "${(pct * 100).toInt()}%",
                    color = if (isOver) DebitColor else Aureate,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Upcoming Bills Strip
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun UpcomingBillsStrip(upcomingItems: List<Triple<String, Double, Long>>) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current
    if (upcomingItems.isEmpty()) return
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayMs = 24 * 60 * 60 * 1000L
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("UPCOMING BILLS", color = colors.secondaryText, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(upcomingItems) { (name, amount, date) ->
                val daysUntil = ((date - today) / dayMs).toInt()
                val dayLabel  = when (daysUntil) {
                    0    -> "Today"
                    1    -> "Tomorrow"
                    else -> SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(date))
                }
                Column(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(colors.cardBg)
                        .border(
                            1.dp,
                            if (daysUntil <= 1) Aureate.copy(alpha = 0.5f) else colors.cardBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(dayLabel,
                        color = if (daysUntil <= 1) Aureate else colors.secondaryText,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = colors.primaryText, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 90.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(fmtAmt(amount, show), color = DebitColor,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN — EMI Tracker
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun EmiTrackerScreen(
    allTransactions: List<ParsedWithCategory>,
    emiPinnedKeys: Set<String> = emptySet(),
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current
    val emiKeywords = listOf(
        // EMI explicit
        "loan emi", "emi debit", "emi debited", "emi paid", "emi towards",
        "loan installment", "loan instalment", "towards emi", "for emi", "your emi",
        "emi of rs", "emi of inr", "emi amount",
        // Loan types (specific enough to not collide with SIP/insurance)
        "home loan", "personal loan", "car loan", "auto loan",
        "vehicle loan", "gold loan", "education loan", "business loan",
        "two wheeler loan", "consumer loan", "loan repayment", "loan payment",
        // NBFCs / lenders (named lenders are unambiguous)
        "bajaj finserv", "bajaj finance", "capital first", "fullerton",
        "muthoot", "mahindra finance", "shriram finance", "tata capital",
        "hdfc credila", "aditya birla finance", "l&t finance", "piramal finance",
        "idfc first", "iifl finance", "manappuram", "cholamandalam",
    )
    // EMI categories: BILLS is the primary, but NACH debits sometimes land in TRANSFER or UNCATEGORIZED
    val emiCategories = setOf(
        Category.BILLS, Category.TRANSFER, Category.UNCATEGORIZED
    )
    data class EmiGroup(val name: String, val amount: Double, val lastDate: Long, val count: Int, val isPinned: Boolean)
    val emiGroups = remember(allTransactions, emiPinnedKeys) {
        allTransactions.filter { tx ->
            !tx.isVoided && tx.sms.type == TxType.DEBIT &&
            (tx.txKey in emiPinnedKeys ||
             (tx.category in emiCategories &&
              emiKeywords.any { kw -> tx.sms.rawText.lowercase().contains(kw) }))
        }.groupBy { tx ->
            tx.sms.merchant?.take(30) ?: tx.sms.bank ?: "Unknown"
        }.map { (name, txs) ->
            val sorted = txs.sortedByDescending { it.date }
            EmiGroup(name, txs.map { it.sms.amount }.average(), sorted.first().date, txs.size,
                isPinned = txs.all { it.txKey in emiPinnedKeys })
        }.sortedByDescending { it.amount }
    }
    val totalMonthly = emiGroups.sumOf { it.amount }
    Column(Modifier.fillMaxSize().background(colors.appBg)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = colors.primaryText, fontSize = 22.sp)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("EMI & Loan Tracker", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${emiGroups.size} active loan${if (emiGroups.size != 1) "s" else ""} detected",
                    color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
        // Total monthly EMI summary
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏦", fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Total Monthly EMI", color = colors.secondaryText, fontSize = 12.sp)
                Text(fmtAmt(totalMonthly, show), color = DebitColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtAmt(totalMonthly * 12, show), color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("per year", color = colors.secondaryText, fontSize = 10.sp)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (emiGroups.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✅", fontSize = 40.sp)
                            Text("No loan EMIs detected", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("NACH mandates, loan instalments & NBFC payments will appear here",
                                color = colors.secondaryText, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(emiGroups) { grp ->
                    val nextDate = grp.lastDate + 30L * 24 * 60 * 60 * 1000
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.cardBg)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(DebitColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center) {
                            Text("🏦", fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(grp.name, color = colors.primaryText, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                if (grp.isPinned) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("📌", fontSize = 11.sp)
                                }
                            }
                            Text("${grp.count} payment${if (grp.count != 1) "s" else ""} detected  •  Next ~${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(nextDate))}",
                                color = colors.secondaryText, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmtAmt(grp.amount, show), color = DebitColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("/mo", color = colors.secondaryText, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN — Subscription Manager
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SubscriptionsScreen(
    allTransactions: List<ParsedWithCategory>,
    recurringKeys: Set<String>,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val show   = LocalShowAmounts.current

    // Known digital subscription keywords — matched against merchant/VPA name
    val subscriptionKeywords = remember {
        setOf(
            // Streaming video
            "netflix", "hotstar", "disneyplus", "disney+", "jiocinema", "sonyliv",
            "zee5", "voot", "altbalaji", "erosnow", "mxplayer", "discovery",
            "primevideo", "amazon prime", "amazonprime",
            // Music / audio
            "spotify", "jiosaavn", "saavn", "gaana", "wynk", "hungama", "apple music",
            // Video / social
            "youtube", "youtubepremium",
            // Anime / international
            "crunchyroll", "curiositystream",
            // Cinema / events
            "bookmyshow", "inox", "pvr", "cinepolis",
            // Education
            "byjus", "unacademy", "vedantu", "coursera", "udemy", "skillshare",
            "linkedin", "toppr", "cuemath", "whitehat",
            // Fitness
            "healthifyme", "cult", "cultfit", "fittr", "practo",
            // Productivity / tools
            "notion", "zoom", "dropbox", "adobe", "canva", "github", "microsoft",
            // CRED (known to appear as cred.club)
            "cred.club", "cred",
            // Common UPI suffixes for subscription services
            "netflixupi", "spotifyupi",
        )
    }

    data class SubEntry(
        val key: String,                // original recurringKey — unique, used as list key
        val name: String, val category: Category,
        val lastAmount: Double, val avgAmount: Double,
        val lastDate: Long, val nextDate: Long,
        val transactions: List<ParsedWithCategory>,
        val isSubscription: Boolean
    )

    val allEntries = remember(allTransactions, recurringKeys) {
        recurringKeys.mapNotNull { key ->
            val matching = allTransactions.filter { tx ->
                !tx.isVoided && tx.sms.type == TxType.DEBIT &&
                (tx.category == Category.BILLS || tx.category == Category.ENTERTAINMENT ||
                 tx.category == Category.EDUCATION || tx.category == Category.HEALTH) &&
                (tx.upiId?.lowercase() == key || tx.sms.merchant?.lowercase()?.trim() == key)
            }.sortedByDescending { it.date }
            if (matching.isEmpty()) return@mapNotNull null
            val last = matching.first()
            val avg  = matching.take(3).map { it.sms.amount }.average()
            val name = last.sms.merchant ?: key.substringBefore("@").replaceFirstChar { it.uppercase() }
            val nameLower = name.lowercase()
            val keyLower  = key.lowercase()
            val isSub = last.category == Category.ENTERTAINMENT ||
                subscriptionKeywords.any { kw -> nameLower.contains(kw) || keyLower.contains(kw) }
            SubEntry(key, name, last.category, last.sms.amount, avg,
                last.date, last.date + 30L * 24 * 60 * 60 * 1000, matching, isSub)
        }.sortedByDescending { it.lastAmount }
    }

    val digitalSubs    = allEntries.filter { it.isSubscription }
    val recurringBills = allEntries.filter { !it.isSubscription }
    val totalSubMonthly  = digitalSubs.sumOf { it.avgAmount }
    val totalBillMonthly = recurringBills.sumOf { it.avgAmount }
    val totalMonthly     = totalSubMonthly + totalBillMonthly
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    var expandedName by remember { mutableStateOf<String?>(null) }

    @Composable
    fun SubEntryCard(sub: SubEntry, isBill: Boolean) {
        val now        = System.currentTimeMillis()
        val daysUntil  = ((sub.nextDate - now) / (24 * 60 * 60 * 1000L)).toInt()
        val isUpcoming = daysUntil in 0..3
        val isExpanded = expandedName == sub.name

        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isUpcoming) Aureate.copy(alpha = 0.06f) else colors.cardBg)
                .border(
                    1.dp,
                    if (isExpanded) colors.primaryText.copy(alpha = 0.25f)
                    else if (isUpcoming) Aureate.copy(alpha = 0.35f)
                    else colors.cardBorder,
                    RoundedCornerShape(14.dp)
                )
                .clickable { expandedName = if (isExpanded) null else sub.name }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(colors.cardSurface), contentAlignment = Alignment.Center) {
                    Text(categoryIcon(sub.category), fontSize = 22.sp)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sub.name, color = colors.primaryText, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isUpcoming) Text("⚡ DUE", color = Aureate, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Next: ${dateFmt.format(Date(sub.nextDate))}  •  ${sub.category.displayName}",
                        color = colors.secondaryText, fontSize = 11.sp)
                    if (isBill) {
                        Text("${sub.transactions.size} charge${if (sub.transactions.size != 1) "s" else ""}  •  tap to see amounts",
                            color = colors.secondaryText.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
                if (!isBill) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmtAmt(sub.lastAmount, show), color = colors.primaryText,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("last charge", color = colors.secondaryText, fontSize = 10.sp)
                        Text(if (isExpanded) "▲" else "▼", color = colors.secondaryText, fontSize = 10.sp)
                    }
                } else {
                    Text(if (isExpanded) "▲" else "▼", color = colors.secondaryText, fontSize = 12.sp)
                }
            }
            if (isExpanded) {
                HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 14.dp))
                sub.transactions.forEach { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dateFmt.format(Date(tx.date)), color = colors.secondaryText, fontSize = 12.sp)
                            val note = tx.sms.merchant ?: tx.sms.bank
                            if (!note.isNullOrBlank()) {
                                Text(note, color = colors.secondaryText.copy(alpha = 0.6f),
                                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text("- ${fmtAmt(tx.sms.amount, show)}", color = DebitColor,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.appBg)) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("←", color = colors.primaryText, fontSize = 22.sp)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Subscription Manager", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${digitalSubs.size} subscriptions  •  ${recurringBills.size} recurring bills",
                    color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Summary card ─────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(18.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Monthly total", color = colors.secondaryText, fontSize = 11.sp)
                        Text(fmtAmt(totalMonthly, show), color = DebitColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Subscriptions  ${fmtAmt(totalSubMonthly, show)}", color = colors.secondaryText, fontSize = 11.sp)
                        Text("Bills  ${fmtAmt(totalBillMonthly, show)}", color = colors.secondaryText, fontSize = 11.sp)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Yearly cost", color = colors.secondaryText, fontSize = 11.sp)
                        Text(fmtAmt(totalMonthly * 12, show), color = colors.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Subscriptions section ─────────────────────────────────────────
            if (digitalSubs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("SUBSCRIPTIONS", color = colors.secondaryText, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                items(digitalSubs, key = { "sub_${it.key}" }) { sub ->
                    SubEntryCard(sub, isBill = false)
                }
            }

            // ── Recurring Bills section ───────────────────────────────────────
            if (recurringBills.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("RECURRING BILLS", color = colors.secondaryText, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                items(recurringBills, key = { "bill_${it.key}" }) { sub ->
                    SubEntryCard(sub, isBill = true)
                }
            }

            if (allEntries.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📱", fontSize = 40.sp)
                            Text("No recurring charges detected", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("Charges that repeat across 2+ months will appear here",
                                color = colors.secondaryText, fontSize = 13.sp, textAlign = TextAlign.Center)
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
// SCREEN 3 — Payment Modes (accordion) — Credit Card / UPI / Debit Card / Net Banking
// Informational only: purely groups already-computed transactions by paymentMethod
// for display. Nothing here feeds into Income / Expense / Savings / Budget totals —
// those are computed elsewhere from category + type, independent of this screen.
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun PaymentMethodsScreen(
    transactions: List<ParsedWithCategory>,
    onBack: () -> Unit,
    selectedPeriod: Period,
    onPeriodChange: (Period) -> Unit,
    onShowDatePicker: () -> Unit,
    customRangeStart: Long?,
    customRangeEnd: Long?
) {
    val debits = transactions.filter {
        it.sms.type == TxType.DEBIT && !it.isVoided && it.category != Category.TRANSFER
    }
    val totalSpent = debits.sumOf { it.sms.amount }.takeIf { it > 0.0 } ?: 1.0

    val byMethod = PaymentMethod.entries
        .map { m -> m to debits.filter { it.paymentMethod == m }.sortedByDescending { it.date } }
        .filter { (_, txns) -> txns.isNotEmpty() }
        .sortedByDescending { (_, txns) -> txns.sumOf { it.sms.amount } }

    var expandedMethods by remember { mutableStateOf(setOf<PaymentMethod>()) }

    Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.appBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(LocalAppColors.current.cardBg).border(1.dp, LocalAppColors.current.cardBorder, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("←", color = LocalAppColors.current.primaryText, fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Payment Modes", color = LocalAppColors.current.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("tap a mode to see its transactions", color = LocalAppColors.current.secondaryText, fontSize = 11.sp)
            }
            PeriodDropdownButton(selectedPeriod, customRangeStart, customRangeEnd, onPeriodChange, onShowDatePicker)
        }
        HorizontalDivider(color = LocalAppColors.current.cardBorder, thickness = 0.5.dp)

        // Disclaimer banner
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Aureate.copy(alpha = 0.08f))
                .border(0.5.dp, Aureate.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                "ℹ️  Informational only. Shows how you paid — Credit Card, UPI, Debit Card, Net Banking. " +
                "These transactions are already counted in Income, Expense, Savings, and Budget by category; nothing here duplicates or changes those calculations.",
                color = LocalAppColors.current.secondaryText, fontSize = 11.sp, lineHeight = 16.sp
            )
        }

        if (byMethod.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💳", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No transactions found\nin this period.",
                        color = LocalAppColors.current.secondaryText, fontSize = 14.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center)
                }
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(byMethod, key = { (m, _) -> m.name }) { (method, txns) ->
                val total      = txns.sumOf { it.sms.amount }
                val pct        = (total / totalSpent).toFloat().coerceIn(0f, 1f)
                val isExpanded = method in expandedMethods
                PaymentModeAccordionCard(
                    method       = method,
                    amount       = total,
                    count        = txns.size,
                    percentage   = pct,
                    expanded     = isExpanded,
                    onToggle     = {
                        expandedMethods = if (isExpanded) expandedMethods - method else expandedMethods + method
                    },
                    transactions = txns
                )
            }
        }
    }
}

// ─── Payment mode accordion card ──────────────────────────────────────────────
@Composable
fun PaymentModeAccordionCard(
    method: PaymentMethod,
    amount: Double,
    count: Int,
    percentage: Float,
    expanded: Boolean,
    onToggle: () -> Unit,
    transactions: List<ParsedWithCategory>
) {
    val color = paymentMethodColor(method)
    val show  = LocalShowAmounts.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalAppColors.current.cardBg)
            .border(1.dp, color.copy(alpha = if (expanded) 0.45f else 0.25f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.clickable { onToggle() }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                        .background(color.copy(alpha = 0.12f))
                        .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(method.icon, fontSize = 20.sp) }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(method.displayName, color = LocalAppColors.current.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$count transaction${if (count != 1) "s" else ""}",
                        color = LocalAppColors.current.secondaryText, fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtAmt(amount, show), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("%.0f%% of spend".format(percentage * 100), color = LocalAppColors.current.secondaryText, fontSize = 10.sp)
                }

                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "▴" else "▾", color = LocalAppColors.current.secondaryText, fontSize = 16.sp)
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

        if (expanded) {
            HorizontalDivider(color = LocalAppColors.current.cardBorder.copy(alpha = 0.5f))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                transactions.forEachIndexed { idx, tx ->
                    PaymentModeTxRow(tx)
                    if (idx < transactions.lastIndex) {
                        HorizontalDivider(color = LocalAppColors.current.cardBorder.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

// ─── One read-only transaction row inside an expanded payment-mode card ──────
@Composable
private fun PaymentModeTxRow(item: ParsedWithCategory) {
    val colors  = LocalAppColors.current
    val show    = LocalShowAmounts.current
    val dateStr = remember(item.date) { smsDateFmt.format(Date(item.date)) }
    val label   = item.sms.merchant
        ?: item.upiId?.substringBefore("@")?.uppercase()
        ?: item.category.displayName
    val account = item.creditCardId ?: item.sms.accountTail?.let { "••••$it" }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(categoryIcon(item.category), fontSize = 15.sp, modifier = Modifier.width(26.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(dateStr, color = colors.secondaryText, fontSize = 10.sp)
                if (account != null) {
                    Text("  •  $account", color = colors.secondaryText, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(fmtAmt(item.sms.amount, show), color = DebitColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
