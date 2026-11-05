package pl.local.nasbrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

private val surface = Color(0xFF142737)
private val accent = Color(0xFF66E4C9)
private val muted = Color(0xFFADC1CE)

/**
 * Right hand panel can collapse to a 68dp action rail, returning its width
 * to the central file browser. Individual accordion states are owned by caller.
 * There are no destructive operations in this component.
 */
@Composable
internal fun NasRightPanel(
    collapsed: Boolean,
    phoneExpanded: Boolean,
    transfersExpanded: Boolean,
    codeExpanded: Boolean,
    recentTransfers: List<RecentTransfer>,
    accessCode: String,
    onCollapse: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenCode: () -> Unit,
    onTogglePhone: () -> Unit,
    onToggleTransfers: () -> Unit,
    onToggleCode: () -> Unit,
    onPair: () -> Unit
) {
    val rightWidth by animateDpAsState(
        targetValue = if (collapsed) 68.dp else 250.dp,
        animationSpec = tween(durationMillis = 240),
        label = "nasRightPanelWidth"
    )
    Column(
        Modifier.width(rightWidth)
            .fillMaxHeight()
            .clipToBounds()
            .background(Color(0xFF102130), RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (collapsed) {
            RightRailAction(NasGlyph.PANEL, "Rozwiń", onCollapse)
            Spacer(Modifier.height(9.dp))
            RightRailAction(NasGlyph.PHONE, "Telefon", onOpenPhone)
            RightRailAction(NasGlyph.CLOCK, "Transfery", onOpenTransfers)
            RightRailAction(NasGlyph.INFO, "Kod", onOpenCode)
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RightExpandedToggle(onCollapse)
                NasAccordion(
                    NasGlyph.PHONE, "Telefon → NAS", phoneExpanded, onTogglePhone
                ) {
                    Text("Wyślij zdjęcia i filmy na dysk bez używania chmury.",
                        color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(11.dp))
                    RightActionButton("Sparuj przez QR", NasGlyph.QR, onPair)
                }
                NasAccordion(
                    NasGlyph.CLOCK, "Ostatnie transfery", transfersExpanded,
                    onToggleTransfers
                ) {
                    if (recentTransfers.isEmpty()) {
                        Text("Nie przesłano jeszcze żadnych plików.",
                            color = muted, fontSize = 12.sp)
                    } else {
                        recentTransfers.take(4).forEach { transfer ->
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                NasIcon(NasGlyph.CHECK, Modifier.size(16.dp), accent)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(transfer.name, color = Color.White,
                                        fontSize = 12.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(nasCompactSize(transfer.size) + " • Zakończono",
                                        color = muted, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                }
                NasAccordion(
                    NasGlyph.INFO, "Kod awaryjny", codeExpanded, onToggleCode
                ) {
                    Text(accessCode, color = accent, fontSize = 23.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Podawaj kod tylko zaufanym urządzeniom w sieci LAN.",
                        color = muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun RightExpandedToggle(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.025f else 1f, tween(170), label = "collapseFocus"
    )
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) accent else Color(0xFF1E3A4D))
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 11.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NasIcon(NasGlyph.PANEL, Modifier.size(20.dp),
            if (focused) Color(0xFF091A24) else accent)
        Spacer(Modifier.width(9.dp))
        Text("Zwiń panele", color = if (focused) Color(0xFF091A24) else Color.White,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RightRailAction(glyph: NasGlyph, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.035f else 1f, tween(150), label = "railFocus"
    )
    Column(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(11.dp))
            .background(if (focused) accent else Color(0xFF1B3444))
            .border(if (focused) 1.dp else 0.dp, Color.White,
                RoundedCornerShape(11.dp))
            .clickable(onClick = onClick).focusable()
            .padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NasIcon(glyph, Modifier.size(22.dp),
            if (focused) Color(0xFF081A23) else accent)
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (focused) Color(0xFF081A23) else muted,
            fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NasAccordion(
    glyph: NasGlyph,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    body: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(surface)
            .animateContentSize(animationSpec = tween(220))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .background(if (focused) Color(0xFF265A5D) else Color.Transparent)
                .clickable(onClick = onToggle).focusable()
                .padding(horizontal = 13.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NasIcon(glyph, Modifier.size(20.dp), accent)
            Spacer(Modifier.width(9.dp))
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(if (expanded) "⌃" else "⌄", color = accent, fontSize = 19.sp)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220)) +
                fadeIn(animationSpec = tween(170)),
            exit = shrinkVertically(animationSpec = tween(190)) +
                fadeOut(animationSpec = tween(150))
        ) {
            Column(Modifier.fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, bottom = 15.dp)) {
                body()
            }
        }
    }
}

@Composable
private fun RightActionButton(
    label: String,
    glyph: NasGlyph,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Row(
        Modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Color.White else accent)
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        NasIcon(glyph, Modifier.size(18.dp), Color(0xFF09212C))
        Spacer(Modifier.width(9.dp))
        Text(label, color = Color(0xFF09212C),
            fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

private fun nasCompactSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
