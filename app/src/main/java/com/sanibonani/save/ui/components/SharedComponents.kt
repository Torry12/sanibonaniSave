package com.sanibonani.save.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.config.Configuration
import android.preference.PreferenceManager

import java.util.Locale
import java.text.NumberFormat
import java.util.Currency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Currency formatter ────────────────────────────────────────────────────────
private val zarFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
    currency = Currency.getInstance("ZAR")
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatZAR(amount: Double): String = zarFormatter.format(amount)

fun formatDecimal(value: Double): String = String.format(Locale("en", "ZA"), "%.2f", value)

fun formatPct(value: Double): String = "${formatDecimal(value)}%"

fun formatZARShort(amount: Double): String =
    when {
        amount >= 1_000_000 -> "R ${String.format(Locale.US, "%.1f", amount / 1_000_000)}M"
        amount >= 1_000     -> "R ${String.format(Locale.US, "%.1f", amount / 1_000)}k"
        else                -> "R ${String.format(Locale.US, "%.0f", amount)}"
    }

// ── Visual Transformations (Type Masking) ──────────────────────────────────────

class CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 16) text.text.substring(0..15) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i % 4 == 3 && i != 15) out += " "
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 7) return offset + 1
                if (offset <= 11) return offset + 2
                if (offset <= 16) return offset + 3
                return 19
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 9) return offset - 1
                if (offset <= 14) return offset - 2
                if (offset <= 19) return offset - 3
                return 16
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

class ExpiryDateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 4) text.text.substring(0..3) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 1) out += "/"
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset
                if (offset <= 4) return offset + 1
                return 5
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset - 1
                return 4
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

class PhoneNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Mask: 000 000 0000
        val trimmed = if (text.text.length >= 10) text.text.substring(0..9) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 2 || i == 5) out += " "
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset + 1
                if (offset <= 10) return offset + 2
                return 12
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 7) return offset - 1
                if (offset <= 12) return offset - 2
                return 10
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

class IDNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // South African ID: 13 digits (###### #### ###)
        val trimmed = if (text.text.length >= 13) text.text.substring(0..12) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 5 || i == 9) out += " "
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 5) return offset
                if (offset <= 9) return offset + 1
                return offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 6) return offset
                if (offset <= 11) return offset - 1
                return offset - 2
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

class DateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // YYYY-MM-DD
        val trimmed = if (text.text.length >= 8) text.text.substring(0..7) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 3 || i == 5) out += "-"
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 6) return offset + 1
                return offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 7) return offset - 1
                return offset - 2
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

// ── Initials Avatar ───────────────────────────────────────────────────────────
@Composable
fun InitialsAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = Forest,
    textColor: Color = Color.White
) {
    Box(
        modifier          = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment  = Alignment.Center
    ) {
        Text(
            text       = initials.take(2).uppercase(),
            style      = MaterialTheme.typography.labelLarge,
            color      = textColor,
            fontSize   = (size.value * 0.32).sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ── Status Chip ───────────────────────────────────────────────────────────────
@Composable
fun StatusChip(status: MemberStatus, label: String? = null) {
    val (bg, fg) = when (status) {
        MemberStatus.ACTIVE          -> SuccessBg to SuccessGreen
        MemberStatus.PROBATION       -> WarningBg to WarningAmber
        MemberStatus.SUSPENDED       -> ErrorBg   to ErrorRed
        MemberStatus.PENDING_PAYMENT -> InfoBg    to InfoBlue
    }
    Surface(
        color  = bg,
        shape  = RoundedCornerShape(20.dp)
    ) {
        Text(
            text      = label ?: status.displayName,
            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PaymentChip(paid: Boolean) {
    val (bg, fg, label) = if (paid)
        Triple(SuccessBg, SuccessGreen, "Paid")
    else
        Triple(WarningBg, WarningAmber, "Due")
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            text      = label,
            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AdminFeeChip(state: AdminFeeState) {
    val (bg, fg, label) = when (state) {
        AdminFeeState.PAID               -> Triple(SuccessBg, SuccessGreen,  "Fee Paid ✓")
        AdminFeeState.DUE                -> Triple(WarningBg, WarningAmber,  "Fee Due")
        AdminFeeState.OVERDUE            -> Triple(ErrorBg,   ErrorRed,      "⚠️ Overdue")
        AdminFeeState.WARNING            -> Triple(ErrorBg,   ErrorRed,      "⚠️ Warning")
        AdminFeeState.SUSPENDED          -> Triple(ErrorBg,   ErrorRed,      "🔴 Suspended")
        AdminFeeState.PENDING_ACTIVATION -> Triple(InfoBg,    InfoBlue,      "Pending Activation")
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            text      = label,
            modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Metric Stat Card ──────────────────────────────────────────────────────────
@Composable
fun StatCard(
    icon: String, label: String, value: String,
    subtitle: String? = null,
    accentColor: Color = Forest,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier  = modifier
            .width(185.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = BorderStroke(1.dp, accentColor.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.15f), accentColor.copy(alpha = 0.05f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) { 
                Text(icon, fontSize = 24.sp) 
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = label, 
                style = MaterialTheme.typography.labelMedium, 
                color = MidGray, 
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = value, 
                style = MaterialTheme.typography.titleLarge, 
                color = Charcoal, 
                fontWeight = FontWeight.Black, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            )
            
            subtitle?.let {
                Text(
                    text = it, 
                    style = MaterialTheme.typography.labelSmall, 
                    color = accentColor.copy(alpha = 0.7f), 
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

// ── Gradient Banner ───────────────────────────────────────────────────────────
@Composable
fun GradientBanner(
    modifier : Modifier = Modifier,
    colors   : List<Color> = listOf(Forest, ForestMid),
    content  : @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(colors), RoundedCornerShape(16.dp))
            .padding(20.dp),
        content  = content
    )
}

// ── Primary Button ────────────────────────────────────────────────────────────
@Composable
fun SanibonaniButton(
    text           : String,
    onClick        : () -> Unit,
    modifier       : Modifier = Modifier,
    enabled        : Boolean  = true,
    isLoading      : Boolean  = false,
    containerColor : Color    = Forest,
    contentColor   : Color    = Color.White,
    textStyle      : androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge
) {
    val alpha = if (enabled) 1f else 0.6f
    Button(
        onClick  = onClick,
        modifier = modifier.height(52.dp).alpha(alpha),
        enabled  = enabled && !isLoading,
        shape    = RoundedCornerShape(16.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor   = containerColor,
            contentColor     = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = contentColor,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GoldButton(
    text     : String,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier,
    enabled  : Boolean  = true,
    isLoading: Boolean  = false
) = SanibonaniButton(text, onClick, modifier, enabled, isLoading, Gold, Forest)

@Composable
fun OutlinedSanibonaniButton(
    text     : String,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier.height(48.dp),
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(2.dp, Forest),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Forest)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

// ── Text Fields ───────────────────────────────────────────────────────────────
@Composable
fun SanibonaniTextField(
    value         : String,
    onValueChange : (String) -> Unit,
    label         : String,
    modifier      : Modifier = Modifier,
    placeholder   : String   = "",
    singleLine    : Boolean  = true,
    isError       : Boolean  = false,
    supportingText : String? = null,
    leadingIcon   : (@Composable () -> Unit)? = null,
    trailingIcon  : (@Composable () -> Unit)? = null,
    prefix        : (@Composable () -> Unit)? = null,
    suffix        : (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    readOnly: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label) },
        placeholder     = { Text(placeholder, color = MidGray) },
        modifier        = modifier.fillMaxWidth(),
        singleLine      = singleLine,
        isError         = isError,
        enabled         = enabled,
        readOnly        = readOnly,
        supportingText  = supportingText?.let { { Text(it) } },
        keyboardOptions = keyboardOptions,
        leadingIcon     = leadingIcon,
        trailingIcon    = trailingIcon,
        prefix          = prefix,
        suffix          = suffix,
        visualTransformation = visualTransformation,
        shape           = RoundedCornerShape(12.dp),
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = ForestLight,
            unfocusedBorderColor = LightGray,
            focusedLabelColor    = Forest,
        )
    )
}

// ── Dropdown ──────────────────────────────────────────────────────────────────
@Composable
fun <T> SanibonaniDropdown(
    label: String,
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    optionToString: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    placeholder: String = "Select Option"
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LightGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Charcoal),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedOption?.let { optionToString(it) } ?: placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionToString(option)) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ── Top App Bar ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanibonaniTopBar(
    title   : String,
    onBack  : (() -> Unit)? = null,
    actions : @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Forest
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Forest)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = Forest
        )
    )
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
fun EmptyState(
    icon        : String,
    title       : String,
    description : String,
    modifier    : Modifier = Modifier
) {
    Column(
        modifier            = modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = Charcoal
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text       = description,
            style      = MaterialTheme.typography.bodyMedium,
            color      = MidGray,
            textAlign  = TextAlign.Center
        )
    }
}

// ── Info Box ──────────────────────────────────────────────────────────────────
enum class InfoType { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun InfoBox(message: String, type: InfoType = InfoType.INFO) {
    val (bg, fg, icon) = when (type) {
        InfoType.INFO    -> Triple(InfoBg, InfoBlue, Icons.Default.Info)
        InfoType.SUCCESS -> Triple(SuccessBg, SuccessGreen, Icons.Default.CheckCircle)
        InfoType.WARNING -> Triple(WarningBg, WarningAmber, Icons.Default.Warning)
        InfoType.ERROR   -> Triple(ErrorBg, ErrorRed, Icons.Default.Error)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = fg, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CenterPlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MidGray)
    }
}

@Composable
fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 1..totalSteps) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= currentStep) Forest else LightGray)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title,
        modifier = modifier.padding(bottom = 8.dp),
        style    = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color    = Forest
    )
}

@Composable
fun GroupCardShimmer() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush())
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier.fillMaxWidth(0.4f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth(0.6f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}

// ── Shimmer Effect ────────────────────────────────────────────────────────────
@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color.LightGray.copy(alpha = 0.6f),
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.6f),
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Charcoal)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MidGray)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MidGray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Charcoal)
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MidGray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

// ── Modern Navigation Link ───────────────────────────────────────────────────
@Composable
fun ModernNavigationLink(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    contentColor: Color = Charcoal,
    accentColor: Color = Forest
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.05f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MidGray
                    )
                }
            }

            if (badgeCount > 0) {
                Surface(
                    color = ErrorRed,
                    shape = CircleShape,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MidGray.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun DashboardHeader(
    title: String,
    subtitle: String,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MidGray, fontWeight = FontWeight.Medium)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Forest)
        }
        Surface(
            onClick = onProfileClick,
            shape = CircleShape,
            color = Forest.copy(0.08f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Forest, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun DashboardHeaderWithNotif(
    title: String,
    subtitle: String,
    notifCount: Int,
    onProfileClick: () -> Unit,
    onNotifClick: () -> Unit,
    profileImageUrl: String? = null,
    profileImageVersion: Long = 0L,
    onLogoutClick: (() -> Unit)? = null,
    onSwitchPortal: (() -> Unit)? = null,
    isPortalSwitchable: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Build a stable ImageRequest - only rebuild when the URL or version actually changes.
    val profileImageRequest = remember(profileImageUrl, profileImageVersion) {
        if (profileImageUrl.isNullOrBlank()) null
        else ImageRequest.Builder(context)
            .data(profileImageUrl)
            .memoryCacheKey("$profileImageUrl-$profileImageVersion")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
    val personPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MidGray)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Forest)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onNotifClick,
                modifier = Modifier.background(Forest.copy(0.1f), CircleShape)
            ) {
                BadgedBox(
                    badge = {
                        if (notifCount > 0) {
                            Badge(
                                containerColor = ErrorRed,
                                contentColor = Color.White
                            ) {
                                Text(notifCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Notifications, null, tint = Forest)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box {
                IconButton(
                    onClick = { if (onLogoutClick != null) showMenu = true else onProfileClick() },
                    modifier = Modifier.background(Forest.copy(0.1f), CircleShape)
                ) {
                    // Always use AsyncImage - Coil handles placeholder/error internally
                    // without triggering recomposition, eliminating the flicker caused by
                    // switching between Icon and AsyncImage composables.
                    AsyncImage(
                        model = profileImageRequest,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = personPainter,
                        error = personPainter,
                        fallback = personPainter
                    )
                }
                
                if (onLogoutClick != null) {
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Profile Settings", color = Charcoal) },
                            onClick = { 
                                showMenu = false
                                onProfileClick() 
                            },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = Forest) }
                        )
                        
                        if (isPortalSwitchable && onSwitchPortal != null) {
                            HorizontalDivider(color = Cream)
                            DropdownMenuItem(
                                text = { Text("Switch Portal", color = Forest) },
                                onClick = { 
                                    showMenu = false
                                    onSwitchPortal() 
                                },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, null, tint = Forest) }
                            )
                        }

                        HorizontalDivider(color = Cream)
                        DropdownMenuItem(
                            text = { Text("Log Out", color = ErrorRed) },
                            onClick = {
                                showMenu = false
                                onLogoutClick()
                            },
                            leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = ErrorRed) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentUploadCard(
    name: String,
    isUploaded: Boolean,
    status: DocumentStatus = DocumentStatus.PENDING,
    onUpload: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isUploaded) Forest.copy(alpha = 0.1f) else LightGray.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isUploaded) {
                            when (status) {
                                DocumentStatus.VERIFIED -> SuccessBg
                                DocumentStatus.REJECTED -> ErrorBg
                                DocumentStatus.PENDING -> WarningBg
                            }
                        } else LightGray.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    !isUploaded -> Icons.Default.FileUpload
                    name.contains("ID", true) -> Icons.Default.Badge
                    name.contains("Residence", true) -> Icons.Default.Home
                    name.contains("Photo", true) -> Icons.Default.AccountCircle
                    name.contains("Constitution", true) || name.contains("Form", true) -> Icons.Default.Description
                    else -> Icons.Default.Description
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isUploaded) {
                        when (status) {
                            DocumentStatus.VERIFIED -> SuccessGreen
                            DocumentStatus.REJECTED -> ErrorRed
                            DocumentStatus.PENDING -> WarningAmber
                        }
                    } else MidGray,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Charcoal
                )
                if (isUploaded) {
                    val (color, label) = when (status) {
                        DocumentStatus.VERIFIED -> SuccessGreen to "Verified Document"
                        DocumentStatus.REJECTED -> ErrorRed to "Rejected - Please re-upload"
                        DocumentStatus.PENDING -> WarningAmber to "Pending Verification"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (status == DocumentStatus.PENDING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp).padding(end = 4.dp),
                                strokeWidth = 1.5.dp,
                                color = WarningAmber
                            )
                        }
                        Text(
                            text = label,
                            color = color,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = "Action required: Upload document",
                        color = MidGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (!isUploaded || status == DocumentStatus.REJECTED) {
                IconButton(
                    onClick = onUpload,
                    modifier = Modifier.background(Forest, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Icons.Default.CloudUpload, 
                        null, 
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDownload != null) {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Default.Visibility, null, tint = Forest, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (status == DocumentStatus.VERIFIED) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = SuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentAdminCard(
    label: String,
    url: String?,
    status: DocumentStatus,
    onVerify: (Boolean) -> Unit,
    onDownload: (String, String) -> Unit
) {
    val isUploaded = !url.isNullOrBlank()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isUploaded) Forest.copy(alpha = 0.1f) else LightGray.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isUploaded) Forest.copy(alpha = 0.05f) else LightGray.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUploaded) Icons.Default.Description else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isUploaded) Forest else MidGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Charcoal
                )
                if (isUploaded) {
                    val (color, statusLabel) = when (status) {
                        DocumentStatus.VERIFIED -> SuccessGreen to "VERIFIED"
                        DocumentStatus.REJECTED -> ErrorRed to "REJECTED"
                        DocumentStatus.PENDING -> WarningAmber to "PENDING"
                    }
                    Text(
                        text = statusLabel,
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else {
                    Text(
                        text = "NOT UPLOADED",
                        color = MidGray,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isUploaded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onDownload(url.orEmpty(), label) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Visibility, "View Document", tint = Forest, modifier = Modifier.size(22.dp))
                    }
                    
                    if (status == DocumentStatus.PENDING) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { onVerify(false) }, 
                            modifier = Modifier
                                .size(32.dp)
                                .background(ErrorRed.copy(0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, "Reject", tint = ErrorRed, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { onVerify(true) }, 
                            modifier = Modifier
                                .size(32.dp)
                                .background(SuccessGreen.copy(0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Check, "Approve", tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        }
                    } else if (status == DocumentStatus.VERIFIED) {
                         Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    } else if (status == DocumentStatus.REJECTED) {
                         Icon(Icons.Default.Cancel, null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ── OSMDroid Composable ───────────────────────────────────────────────────────
@Composable
fun SaOsmMap(
    groups   : List<Group>,
    onMarker : (String) -> Unit,
    onLocationTap: ((List<Group>) -> Unit)? = null,
    userLocation: android.location.Location? = null,
    autoCenterOnGroups: Boolean = true,
    modifier : Modifier = Modifier
) {
    val context = LocalContext.current
    var lastUserViewportKey by remember { mutableStateOf<String?>(null) }
    var zoomControlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(zoomControlsVisible) {
        if (zoomControlsVisible) {
            kotlinx.coroutines.delay(3000)
            zoomControlsVisible = false
        }
    }

    // Initialize OSMDroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Start with a South Africa-wide viewport for discovery maps.
            controller.setZoom(5.3)
            controller.setCenter(GeoPoint(-29.0, 24.0))
        }
    }

    val markerEntries = remember(groups) {
        groups.mapNotNull { g ->
            val lat = g.latitude
            val lon = g.longitude
            if (lat != null && lon != null) Triple(lat, lon, g) else null
        }
    }

    // Pre-compute grouped locations on a background thread to avoid blocking the main thread
    var groupedLocationsState by remember { mutableStateOf<Map<String, List<Triple<Double, Double, Group>>>>(emptyMap()) }
    var shouldCenterMap by remember { mutableStateOf(false) }
    var avgLat by remember { mutableStateOf(0.0) }
    var avgLon by remember { mutableStateOf(0.0) }
    var centerZoom by remember { mutableStateOf(10.0) }

    // Compute grouped locations asynchronously to avoid UI thread blocking
    LaunchedEffect(markerEntries) {
        if (markerEntries.isEmpty()) {
            groupedLocationsState = emptyMap()
            shouldCenterMap = false
        } else {
            // Move expensive computation off the main thread
            val grouped = markerEntries
                .groupBy { (lat, lon, _) ->
                    "${"%.4f".format(Locale.US, lat)}:${"%.4f".format(Locale.US, lon)}"
                }
            groupedLocationsState = grouped

            if (autoCenterOnGroups) {
                avgLat = markerEntries.map { it.first }.average()
                avgLon = markerEntries.map { it.second }.average()
                centerZoom = if (markerEntries.size == 1) 14.0 else 10.0
                shouldCenterMap = true
            }
        }
    }

    // Lifecycle handling for OSMDroid MapView
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { mapView },
            update = { }
        )

        LaunchedEffect(markerEntries, userLocation, autoCenterOnGroups) {
            mapView.post {
                mapView.overlays.clear()

                if (userLocation != null && !autoCenterOnGroups) {
                    val lat = userLocation.latitude
                    val lon = userLocation.longitude
                    val viewportKey = "${"%.4f".format(Locale.US, lat)}:${"%.4f".format(Locale.US, lon)}"
                    if (lastUserViewportKey != viewportKey) {
                        val radiusKm = 50.0
                        val latDelta = radiusKm / 111.0
                        val cosLat = kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.01)
                        val lonDelta = radiusKm / (111.0 * cosLat)

                        val bounds = BoundingBox(
                            lat + latDelta,
                            lon + lonDelta,
                            lat - latDelta,
                            lon - lonDelta
                        )

                        mapView.zoomToBoundingBox(bounds, true, 64)
                        lastUserViewportKey = viewportKey
                    }
                }

                // Group near-identical coordinates so one pin can represent multiple groups at a location.
                val groupedLocations = groupedLocationsState.values

                if (shouldCenterMap && markerEntries.isNotEmpty()) {
                    mapView.controller.animateTo(GeoPoint(avgLat, avgLon))
                    mapView.controller.setZoom(centerZoom)
                }

                groupedLocations.forEach { locationEntries ->
                    val lat = locationEntries.map { it.first }.average()
                    val lon = locationEntries.map { it.second }.average()
                    val locationGroups = locationEntries.map { it.third }
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(lat, lon)
                        title = if (locationGroups.size == 1) {
                            locationGroups.first().name
                        } else {
                            "${locationGroups.size} groups at this location"
                        }
                        snippet = if (locationGroups.size == 1) {
                            val group = locationGroups.first()
                            "${group.type.displayName} • ${group.currentMembers} members"
                        } else {
                            locationGroups.take(3).joinToString(" • ") { it.name }
                        }
                        setOnMarkerClickListener { _, _ ->
                            if (locationGroups.size == 1) {
                                onMarker(locationGroups.first().id ?: "")
                            } else {
                                onLocationTap?.invoke(locationGroups)
                                    ?: onMarker(locationGroups.first().id ?: "")
                            }
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                mapView.invalidate()
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = zoomControlsVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = {
                            mapView.controller.zoomIn()
                            zoomControlsVisible = true
                        },
                        containerColor = Color.White.copy(alpha = 0.85f),
                        contentColor = Forest
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom in")
                    }

                    SmallFloatingActionButton(
                        onClick = {
                            mapView.controller.zoomOut()
                            zoomControlsVisible = true
                        },
                        containerColor = Color.White.copy(alpha = 0.85f),
                        contentColor = Forest
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom out")
                    }
                }
            }

            AnimatedVisibility(visible = !zoomControlsVisible) {
                SmallFloatingActionButton(
                    onClick = { zoomControlsVisible = true },
                    containerColor = Color.White.copy(alpha = 0.75f),
                    contentColor = Forest
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Show zoom controls")
                }
            }
        }
    }
}

// ── In-App File View / Download Choice ───────────────────────────────────────

@Composable
fun FileActionDialog(
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onDownload: () -> Unit,
    fileName: String = "File"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FilePresent, null, tint = Forest) },
        title = { Text("File Options") },
        text = { Text("Would you like to view '$fileName' inside the app or download it to your device?") },
        confirmButton = {
            Button(onClick = { onView(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                Text("View In-App")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDownload(); onDismiss() }) {
                Text("Download", color = Forest)
            }
        }
    )
}

@Composable
fun FileViewerDialog(
    url: String,
    fileName: String,
    headers: Map<String, String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPdf = url.substringAfterLast(".", "").substringBefore("?").lowercase() == "pdf"
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.9f)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                    Text(
                        fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (isPdf) {
                        PdfViewer(url, headers)
                    } else {
                        ImageViewer(url, headers)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageViewer(url: String, headers: Map<String, String>) {
    val request = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }
        .crossfade(true)
        .build()

    AsyncImage(
        model = request,
        contentDescription = "Document Image",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun PdfViewer(url: String, headers: Map<String, String>) {
    val context = LocalContext.current
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf")
                val connection = URL(url).openConnection()
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                
                connection.getInputStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val pageCount = renderer.pageCount
                val list = mutableListOf<Bitmap>()
                
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    list.add(bitmap)
                    page.close()
                }
                
                renderer.close()
                fd.close()
                file.delete()
                
                withContext(Dispatchers.Main) {
                    bitmaps = list
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: "Failed to load PDF"
                    isLoading = false
                }
            }
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Forest)
        }
    } else if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: $error", color = Color.White)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(bitmaps) { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanibonaniDatePicker(
    state: DatePickerState,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { 
                onConfirm(state.selectedDateMillis)
                onDismiss()
            }) {
                Text("OK", color = Forest, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Forest)
            }
        },
        colors = DatePickerDefaults.colors(
            containerColor = Color.White
        )
    ) {
        DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = Forest,
                selectedDayContentColor = Color.White,
                todayContentColor = Forest,
                todayDateBorderColor = Forest
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanibonaniDatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            if (value.isNotBlank()) {
                java.time.LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else null
        } catch (_: Exception) { null }
    )

    Box(modifier = modifier) {
        SanibonaniTextField(
            value = value,
            onValueChange = { /* Read only */ },
            label = label,
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = Forest)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Transparent overlay to catch clicks on the whole field
        Box(
            Modifier
                .matchParentSize()
                .clickable { showDialog = true }
        )
    }

    if (showDialog) {
        SanibonaniDatePicker(
            state = datePickerState,
            onDismiss = { showDialog = false },
            onConfirm = { millis ->
                millis?.let {
                    val date = Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    onValueChange(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
            }
        )
    }
}
