package ir.mobileshop.inventory

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val HERO_IMAGES = listOf(R.drawable.bg_aerial, R.drawable.bg_ruins, R.drawable.bg_hall)

/** Which Bishapur photo the header shows right now; App changes it every few seconds. */
val LocalHeroIndex = compositionLocalOf { 0 }

/** Photo banner at the top of every screen, with rounded bottom corners. */
@Composable
fun HeroHeader(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val index = LocalHeroIndex.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(height + statusBar)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    ) {
        Crossfade(
            targetState = HERO_IMAGES[index % HERO_IMAGES.size],
            animationSpec = tween(900),
            label = "hero"
        ) { res ->
            Image(
                painter = painterResource(res),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xCC151824), Color(0x80151824), Color(0xD9151824))
                )
            )
        )
        content()
    }
}

/** Solid bar behind the status bar so scrolling content never runs under its white icons. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(HeroDark))
}

@Composable
fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, description, tint = Color.White) }
}

@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

/** One-line sync state. Tap to sync now (or open the settings when not set up yet). */
@Composable
fun SyncChip(status: SyncStatus, onClick: () -> Unit) {
    val (text, color) = when (status) {
        SyncStatus.NotConfigured -> "همگام‌سازی تنظیم نشده؛ برای اتصال به سرور بزنید" to InkGrey
        SyncStatus.Syncing -> "در حال همگام‌سازی…" to Red
        is SyncStatus.Ok ->
            (if (status.at == 0L) "همگام‌سازی فعال است" else "همگام‌سازی شد ${clock(status.at)}") to GoodGreen
        is SyncStatus.Failed -> "${status.message}؛ تغییرات ذخیره شده و بعداً ارسال می‌شود" to Red
    }
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)
    )
}

@Composable
fun shopFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = Red,
    unfocusedBorderColor = CardBorder,
    focusedLabelColor = Red,
    cursorColor = Red
)

@Composable
fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) RedSoft else Color.White)
            .border(1.dp, if (selected) Red else CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) Red else InkDark,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun clock(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(ms)).fa()
