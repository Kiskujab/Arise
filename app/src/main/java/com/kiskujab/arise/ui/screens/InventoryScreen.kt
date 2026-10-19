package com.kiskujab.arise.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.core.ItemType
import com.kiskujab.arise.core.Rules
import com.kiskujab.arise.core.Shop
import com.kiskujab.arise.ui.components.reveal
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.glowBorder
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType

/** Consumable items: use them, or buy more with gold. */
@Composable
fun BagTab(vm: MainViewModel) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ItemType?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SystemPanel(title = "INVENTORY", modifier = Modifier.reveal(0)) {
            Text(
                "Items drop after cleared Daily Quests, at every safe-zone level, on streak milestones and from Weekly Raids. Most can also be bought with gold.",
                style = AriseType.small,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val r = today?.record
            if (r != null && (r.booster || r.shield || r.skipDay)) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    if (r.booster) Chip("BOOSTER ACTIVE", AriseColors.Blue)
                    if (r.shield) Chip("SHIELD ACTIVE", AriseColors.Cyan)
                    if (r.skipDay) Chip("SKIP DAY ACTIVE", AriseColors.Gold)
                }
            }
        }
        ItemType.entries.forEachIndexed { i, item ->
            val count = state.count(item)
            val price = Shop.prices[item]
            ItemCard(
                item, count, onUse = { confirm = item },
                price = price, canBuy = price != null && state.gold >= price,
                onBuy = { vm.buyItem(item) },
                modifier = Modifier.reveal(i + 1),
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    confirm?.let { item ->
        Dialog(onDismissRequest = { confirm = null }) {
            SystemPanel(title = "CONFIRM", accent = Color(item.rarity.color)) {
                Text(
                    when (item) {
                        ItemType.RANDOM_BOX -> "Open the Random Box?"
                        else -> "Use ${item.displayName} for today?\n\n${item.description}"
                    },
                    style = AriseType.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    SystemButton("CANCEL", onClick = { confirm = null }, accent = AriseColors.TextDim)
                    SystemButton("USE", onClick = { vm.useItem(item); confirm = null }, accent = Color(item.rarity.color))
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: ItemType,
    count: Int,
    onUse: () -> Unit,
    price: Int?,
    canBuy: Boolean,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Color(item.rarity.color)
    SystemPanel(modifier = modifier, accent = if (count > 0) c else AriseColors.TextFaint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemGlyph(item, c, count > 0)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowText(item.displayName, AriseType.bodyBold, color = if (count > 0) AriseColors.Text else AriseColors.TextDim, glow = c)
                    Spacer(Modifier.width(8.dp))
                    Text("×$count" + if (item == ItemType.STREAK_FREEZE) " / ${Rules.MAX_FREEZES}" else "", style = AriseType.number.copy(color = c, fontSize = AriseType.number.fontSize * 0.75f))
                }
                Text(item.rarity.label.uppercase(), style = AriseType.label.copy(color = c))
                Spacer(Modifier.height(4.dp))
                Text(item.description, style = AriseType.small)
            }
        }
        val usable = item.usable && count > 0
        if (usable || price != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (price != null) SystemButton("BUY · $price G", onClick = onBuy, accent = AriseColors.Gold, enabled = canBuy)
                if (usable) SystemButton(if (item == ItemType.RANDOM_BOX) "OPEN" else "USE TODAY", onClick = onUse, accent = c)
            }
        }
    }
}

/** Simple vector glyphs, one per item. */
@Composable
private fun ItemGlyph(item: ItemType, color: Color, active: Boolean) {
    val c = if (active) color else AriseColors.TextFaint
    Canvas(Modifier.size(48.dp).glowBorder(c, 6.dp, if (active) 0.8f else 0.3f).padding(10.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.5.dp.toPx())
        when (item) {
            ItemType.STREAK_FREEZE -> {
                for (i in 0 until 3) {
                    val a = Math.toRadians(i * 60.0)
                    val dx = (kotlin.math.cos(a) * w / 2).toFloat()
                    val dy = (kotlin.math.sin(a) * h / 2).toFloat()
                    drawLine(c, Offset(w / 2 - dx, h / 2 - dy), Offset(w / 2 + dx, h / 2 + dy), 2.5.dp.toPx())
                }
            }
            ItemType.XP_BOOSTER -> {
                val p = Path().apply {
                    moveTo(w * 0.6f, 0f); lineTo(w * 0.15f, h * 0.55f); lineTo(w * 0.5f, h * 0.55f)
                    lineTo(w * 0.4f, h); lineTo(w * 0.85f, h * 0.42f); lineTo(w * 0.5f, h * 0.42f); close()
                }
                drawPath(p, c, style = stroke)
            }
            ItemType.RANDOM_BOX -> {
                drawRect(c, Offset(0f, h * 0.3f), androidx.compose.ui.geometry.Size(w, h * 0.7f), style = stroke)
                drawLine(c, Offset(0f, h * 0.3f), Offset(w * 0.5f, 0f), 2.5.dp.toPx())
                drawLine(c, Offset(w, h * 0.3f), Offset(w * 0.5f, 0f), 2.5.dp.toPx())
                drawLine(c, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h), 2.5.dp.toPx())
            }
            ItemType.PENALTY_SHIELD -> {
                val p = Path().apply {
                    moveTo(w / 2, 0f); lineTo(w, h * 0.18f); lineTo(w * 0.9f, h * 0.65f)
                    lineTo(w / 2, h); lineTo(w * 0.1f, h * 0.65f); lineTo(0f, h * 0.18f); close()
                }
                drawPath(p, c, style = stroke)
            }
            ItemType.SKIP_DAY -> {
                val p = Path().apply {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) w / 2 else w / 4.6f
                        val a = Math.toRadians(-90.0 + i * 36.0)
                        val x = (w / 2 + kotlin.math.cos(a) * r).toFloat()
                        val y = (h / 2 + kotlin.math.sin(a) * r).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(p, c, style = stroke)
            }
        }
    }
}
