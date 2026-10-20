package com.kiskujab.arise.ui.screens

import com.kiskujab.arise.ui.components.popIn
import com.kiskujab.arise.ui.components.tilt3d
import com.kiskujab.arise.ui.components.SpinningCoin
import com.kiskujab.arise.ui.components.ManaCore
import com.kiskujab.arise.ui.components.HexSigil
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.core.Equipment
import com.kiskujab.arise.core.Loadout
import com.kiskujab.arise.core.PlayerState
import com.kiskujab.arise.core.Rank
import com.kiskujab.arise.core.Rarity
import com.kiskujab.arise.core.Rune
import com.kiskujab.arise.core.RuneSlot
import com.kiskujab.arise.core.Slots
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.CountUpText
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.SystemBar
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.glowBorder
import com.kiskujab.arise.ui.components.reveal
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlin.math.cos
import kotlin.math.sin

private enum class GearTab(val label: String) { LOADOUT("LOADOUT"), RUNES("RUNES"), ARMORY("ARMORY"), BAG("BAG") }

private val KEYSTONE_COLOR = AriseColors.Gold
private val MINOR_COLOR = AriseColors.Cyan

/**
 * Runes (free once unlocked) and equipment (unlocked, then bought with gold) make up the loadout.
 * Loadout changes apply from tomorrow, so gear can't be swapped after a bad day.
 */
@Composable
fun GearScreen(vm: MainViewModel) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val day = today?.epochDay ?: return
    var tab by rememberSaveable { mutableStateOf(GearTab.LOADOUT) }
    val active = state.loadoutFor(day)
    val saved = state.loadoutFor(day + 1)
    var draft by remember(saved) { mutableStateOf(saved) }
    val dirty = draft != saved

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlowText("GEAR", AriseType.title, modifier = Modifier.weight(1f))
            SpinningCoin(20.dp)
            Spacer(Modifier.width(8.dp))
            CountUpText(state.gold, AriseType.number.copy(color = AriseColors.Gold), durationMillis = 600, glow = AriseColors.Gold, suffix = " G")
        }
        GearTabs(tab) { tab = it }
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(380, delayMillis = 80)) + slideInHorizontally(tween(420)) { it / 3 * dir } + scaleIn(tween(420), initialScale = 0.92f)) togetherWith
                        (fadeOut(tween(260)) + slideOutHorizontally(tween(320)) { -it / 3 * dir } + scaleOut(tween(320), targetScale = 0.92f))
                },
                label = "gearTab",
            ) { t ->
                when (t) {
                    GearTab.LOADOUT -> LoadoutTab(state, draft, active != saved, onChange = { draft = it }, onBrowse = { tab = it })
                    GearTab.RUNES -> RunesTab(state, draft, onChange = { draft = it })
                    GearTab.ARMORY -> ArmoryTab(state, draft, onChange = { draft = it }, onBuy = { vm.buyEquipment(it) })
                    GearTab.BAG -> BagTab(vm)
                }
            }
        }
        AnimatedVisibility(dirty, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            SystemPanel(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), accent = AriseColors.Gold) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("UNSAVED LOADOUT", style = AriseType.label.copy(color = AriseColors.Gold))
                        Text("Saved changes apply from tomorrow.", style = AriseType.small)
                    }
                    Text("RESET", style = AriseType.label.copy(color = AriseColors.TextDim), modifier = Modifier.clickable { draft = saved }.padding(10.dp))
                    SystemButton("SAVE", onClick = { vm.setLoadout(draft) }, accent = AriseColors.Gold)
                }
            }
        }
    }
}

@Composable
private fun GearTabs(selected: GearTab, onSelect: (GearTab) -> Unit) {
    val pos by animateFloatAsState(selected.ordinal.toFloat(), spring(dampingRatio = 0.6f, stiffness = 320f), label = "gearTabPos")
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .height(38.dp)
            .border(1.dp, AriseColors.Blue.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
    ) {
        val w = maxWidth / GearTab.entries.size
        Box(
            Modifier
                .offset(x = w * pos)
                .width(w)
                .fillMaxHeight()
                .padding(2.dp)
                .background(Brush.verticalGradient(listOf(AriseColors.Cyan.copy(alpha = 0.28f), AriseColors.BlueDeep.copy(alpha = 0.18f))), RoundedCornerShape(3.dp))
                .border(1.dp, AriseColors.Cyan, RoundedCornerShape(3.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            for (t in GearTab.entries) {
                val sel = t == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.label, style = AriseType.label.copy(color = if (sel) AriseColors.Text else AriseColors.TextDim))
                }
            }
        }
    }
}

/** The equipped loadout as an orbit: one light per rune and item circling the hunter's core. */
@Composable
private fun LoadoutAltar(draft: Loadout, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "altar")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "spin")
    val orbs = buildList {
        draft.keystone?.let { add(KEYSTONE_COLOR to 1.3f) }
        draft.minors.forEach { add(MINOR_COLOR to 1f) }
        draft.items.forEach { add(Color(it.rarity.color) to 1.15f) }
    }
    Box(modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        HexSigil(1f, AriseColors.Purple.copy(alpha = 0.6f), Modifier.size(170.dp))
        ManaCore(110.dp, AriseColors.Cyan, energy = 0.4f + 0.1f * orbs.size)
        Canvas(Modifier.size(170.dp)) {
            val c = center
            val r = size.minDimension / 2 * 0.78f
            orbs.forEachIndexed { i, (color, k) ->
                val a = Math.toRadians((spin + i * 360f / orbs.size.coerceAtLeast(1)).toDouble())
                // A tilted orbit: y squashed so it reads as a ring in 3D.
                val depth = sin(a).toFloat()
                val o = Offset(c.x + (cos(a) * r).toFloat(), c.y + depth * r * 0.38f)
                val sz = (4f + 2f * depth) * k
                drawCircle(color.copy(alpha = 0.25f), (sz * 2.6f).dp.toPx(), o)
                drawCircle(color, sz.dp.toPx(), o)
                drawCircle(Color.White.copy(alpha = 0.7f), (sz * 0.4f).dp.toPx(), o)
            }
        }
        if (orbs.isEmpty()) Text("NO GEAR EQUIPPED", style = AriseType.label.copy(color = AriseColors.TextFaint), modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ------------------------------------------------------------------ loadout

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadoutTab(state: PlayerState, draft: Loadout, pendingSaved: Boolean, onChange: (Loadout) -> Unit, onBrowse: (GearTab) -> Unit) {
    val level = state.level
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LoadoutAltar(draft, Modifier.reveal(0))
        if (pendingSaved) {
            SystemPanel(Modifier.reveal(0), accent = AriseColors.Purple) {
                Text("A new loadout is saved. It becomes active tomorrow; today the previous one still applies.", style = AriseType.small)
            }
        }
        SystemPanel(Modifier.reveal(1), title = "RUNE PAGE", accent = KEYSTONE_COLOR) {
            Text("KEYSTONE", style = AriseType.label.copy(color = KEYSTONE_COLOR))
            Spacer(Modifier.height(6.dp))
            if (Slots.keystoneOpen(level)) {
                val k = draft.keystone
                SlotRow(
                    name = k?.displayName ?: "Empty keystone slot",
                    desc = k?.description ?: "Pick a keystone in RUNES.",
                    color = KEYSTONE_COLOR, filled = k != null, keystone = true,
                    onClick = { if (k != null) onChange(draft.copy(keystone = null)) else onBrowse(GearTab.RUNES) },
                )
            } else {
                LockedSlot("Opens at level ${Slots.KEYSTONE_LEVEL}")
            }
            Spacer(Modifier.height(14.dp))
            Text("MINOR RUNES", style = AriseType.label.copy(color = MINOR_COLOR))
            Spacer(Modifier.height(6.dp))
            Slots.MINOR_LEVELS.forEachIndexed { i, lvl ->
                if (level >= lvl) {
                    val r = draft.minors.getOrNull(i)
                    SlotRow(
                        name = r?.displayName ?: "Empty minor slot",
                        desc = r?.description ?: "Pick a minor rune in RUNES.",
                        color = MINOR_COLOR, filled = r != null, keystone = false,
                        onClick = { if (r != null) onChange(draft.copy(minors = draft.minors - r)) else onBrowse(GearTab.RUNES) },
                    )
                } else {
                    LockedSlot("Opens at level $lvl")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        SystemPanel(Modifier.reveal(2), title = "EQUIPMENT", accent = AriseColors.Purple) {
            for (i in 0 until 6) {
                if (i < Slots.itemSlots(level)) {
                    val e = draft.items.getOrNull(i)
                    SlotRow(
                        name = e?.displayName ?: "Empty item slot",
                        desc = e?.description ?: "Buy and equip gear in the ARMORY.",
                        color = e?.let { Color(it.rarity.color) } ?: AriseColors.Purple, filled = e != null, keystone = false,
                        onClick = { if (e != null) onChange(draft.copy(items = draft.items - e)) else onBrowse(GearTab.ARMORY) },
                        equipment = e,
                    )
                } else {
                    val lvl = Slots.itemSlotLevel(i)
                    LockedSlot("Opens at rank ${Rank.forLevel(lvl).label} (level $lvl)")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Text(
            "Tap an equipped rune or item to remove it. Gear does nothing on a day you don't report.",
            style = AriseType.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(3),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SlotRow(name: String, desc: String, color: Color, filled: Boolean, keystone: Boolean, onClick: () -> Unit, equipment: Equipment? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = if (filled) 0.7f else 0.25f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = if (filled) 0.08f else 0.02f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            equipment != null -> EquipmentGlyph(equipment, true, 40.dp)
            else -> RuneGlyph(keystone, color, filled, 40.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = AriseType.bodyBold.copy(color = if (filled) AriseColors.Text else AriseColors.TextDim))
            Text(desc, style = AriseType.small)
        }
        if (filled) Text("✕", style = AriseType.label.copy(color = AriseColors.TextFaint))
    }
}

@Composable
private fun LockedSlot(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, AriseColors.TextFaint.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔒", style = AriseType.body)
        Spacer(Modifier.width(12.dp))
        Text(text, style = AriseType.small.copy(color = AriseColors.TextFaint))
    }
}

// ------------------------------------------------------------------ runes

@Composable
private fun RunesTab(state: PlayerState, draft: Loadout, onChange: (Loadout) -> Unit) {
    val level = state.level
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Runes are passive perks. Unlock them by playing; they're free to equip. One keystone, up to ${Slots.MINOR_LEVELS.size} minor runes.",
            style = AriseType.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(0),
        )
        Rune.entries.forEachIndexed { i, r ->
            val unlocked = r.unlock.isMet(state)
            val equipped = draft.has(r)
            val color = if (r.slot == RuneSlot.KEYSTONE) KEYSTONE_COLOR else MINOR_COLOR
            val canEquip = when (r.slot) {
                RuneSlot.KEYSTONE -> Slots.keystoneOpen(level)
                RuneSlot.MINOR -> draft.minors.size < Slots.minorSlots(level)
            }
            SystemPanel(Modifier.reveal(i + 1, 45).tilt3d(4f), accent = if (unlocked) color else AriseColors.TextFaint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuneGlyph(r.slot == RuneSlot.KEYSTONE, color, unlocked, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlowText(r.displayName, AriseType.bodyBold, color = if (unlocked) AriseColors.Text else AriseColors.TextDim, glow = color)
                            Spacer(Modifier.width(8.dp))
                            if (equipped) Chip("EQUIPPED", color)
                        }
                        Text(r.slot.label.uppercase(), style = AriseType.label.copy(color = color))
                        Text(r.description, style = AriseType.small)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (!unlocked) {
                    UnlockProgress(r.unlock.text, r.unlock.progress(state), r.unlock.target, r.unlock.fraction(state))
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        when {
                            equipped -> SystemButton("REMOVE", onClick = {
                                onChange(if (r.slot == RuneSlot.KEYSTONE) draft.copy(keystone = null) else draft.copy(minors = draft.minors - r))
                            }, accent = AriseColors.TextDim)
                            r.slot == RuneSlot.KEYSTONE -> SystemButton(
                                if (draft.keystone != null) "SWAP IN" else "EQUIP",
                                onClick = { onChange(draft.copy(keystone = r)) }, accent = color, enabled = canEquip,
                            )
                            else -> SystemButton("EQUIP", onClick = { onChange(draft.copy(minors = draft.minors + r)) }, accent = color, enabled = canEquip)
                        }
                    }
                    if (!equipped && !canEquip) {
                        Text(
                            if (r.slot == RuneSlot.KEYSTONE) "Keystone slot opens at level ${Slots.KEYSTONE_LEVEL}." else "No free minor slot. Remove one first or level up.",
                            style = AriseType.small.copy(color = AriseColors.TextFaint),
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UnlockProgress(text: String, current: Long, target: Long, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🔒 $text", style = AriseType.small, modifier = Modifier.weight(1f))
        Text("${current.coerceAtMost(target)} / $target", style = AriseType.label)
    }
    Spacer(Modifier.height(4.dp))
    SystemBar(fraction, height = 5.dp, color = AriseColors.TextDim)
}

// ------------------------------------------------------------------ armory

@Composable
private fun ArmoryTab(state: PlayerState, draft: Loadout, onChange: (Loadout) -> Unit, onBuy: (Equipment) -> Unit) {
    val slots = Slots.itemSlots(state.level)
    var confirm by remember { mutableStateOf<Equipment?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Unlock gear by playing, buy it with gold, equip it in an item slot. You have $slots item slot${if (slots > 1) "s" else ""}; each new rank adds one.",
            style = AriseType.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(0),
        )
        Equipment.entries.forEachIndexed { i, e ->
            val unlocked = e.unlock.isMet(state)
            val owned = e in state.equipment
            val equipped = draft.has(e)
            val color = Color(e.rarity.color)
            SystemPanel(Modifier.reveal(i + 1, 45).tilt3d(4f), accent = if (unlocked) color else AriseColors.TextFaint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EquipmentGlyph(e, unlocked, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlowText(e.displayName, AriseType.bodyBold, color = if (unlocked) AriseColors.Text else AriseColors.TextDim, glow = color)
                            Spacer(Modifier.width(8.dp))
                            if (equipped) Chip("EQUIPPED", color) else if (owned) Chip("OWNED", AriseColors.TextDim)
                        }
                        Text(e.rarity.label.uppercase(), style = AriseType.label.copy(color = color))
                        Text(e.description, style = AriseType.small)
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    !unlocked -> UnlockProgress(e.unlock.text, e.unlock.progress(state), e.unlock.target, e.unlock.fraction(state))
                    !owned -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (state.gold < e.price) Text("Need ${e.price - state.gold} more gold  ", style = AriseType.small)
                        SystemButton("BUY · ${e.price} G", onClick = { confirm = e }, accent = AriseColors.Gold, enabled = state.gold >= e.price)
                    }
                    equipped -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SystemButton("REMOVE", onClick = { onChange(draft.copy(items = draft.items - e)) }, accent = AriseColors.TextDim)
                    }
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (draft.items.size >= slots) Text("All slots full  ", style = AriseType.small)
                        SystemButton("EQUIP", onClick = { onChange(draft.copy(items = draft.items + e)) }, accent = color, enabled = draft.items.size < slots)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    confirm?.let { e ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirm = null }) {
            SystemPanel(Modifier.popIn(), title = "ARMORY", accent = AriseColors.Gold) {
                EquipmentGlyph(e, true, 64.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text("Buy ${e.displayName} for ${e.price} gold?\n\n${e.description}", style = AriseType.body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    SystemButton("CANCEL", onClick = { confirm = null }, accent = AriseColors.TextDim)
                    SystemButton("BUY", onClick = { onBuy(e); confirm = null }, accent = AriseColors.Gold)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ glyphs

@Composable
fun RuneGlyph(keystone: Boolean, color: Color, active: Boolean, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = if (active) color else AriseColors.TextFaint
    Canvas(modifier.size(size).glowBorder(c, size / 2, if (active) 0.7f else 0.25f).padding(size / 5)) {
        val w = this.size.width
        val h = this.size.height
        val sw = 2.dp.toPx()
        if (keystone) {
            val p = Path().apply { moveTo(w / 2, 0f); lineTo(w, h / 2); lineTo(w / 2, h); lineTo(0f, h / 2); close() }
            drawPath(p, c, style = Stroke(sw))
            val q = Path().apply { moveTo(w / 2, h * 0.25f); lineTo(w * 0.75f, h / 2); lineTo(w / 2, h * 0.75f); lineTo(w * 0.25f, h / 2); close() }
            drawPath(q, c.copy(alpha = 0.6f))
        } else {
            drawCircle(c, w / 2, style = Stroke(sw))
            drawLine(c, Offset(w / 2, h * 0.15f), Offset(w / 2, h * 0.85f), sw)
            drawLine(c, Offset(w / 2, h * 0.4f), Offset(w * 0.75f, h * 0.22f), sw)
            drawLine(c, Offset(w / 2, h * 0.6f), Offset(w * 0.25f, h * 0.42f), sw)
        }
    }
}

@Composable
fun EquipmentGlyph(e: Equipment, active: Boolean, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = if (active) Color(e.rarity.color) else AriseColors.TextFaint
    val glow = if (e.rarity == Rarity.LEGENDARY) 1f else 0.7f
    Canvas(modifier.size(size).glowBorder(c, 6.dp, if (active) glow else 0.25f).padding(size / 5)) {
        val sw = 2.2.dp.toPx()
        when (e) {
            Equipment.INITIATES_BLADE, Equipment.EDGE_OF_INFINITY -> blade(c, sw, e == Equipment.EDGE_OF_INFINITY)
            Equipment.GOLDEN_IDOL -> star(c, sw, 5)
            Equipment.SWIFTSTRIDE_GREAVES -> boot(c, sw)
            Equipment.THORN_PLATE, Equipment.WARLORDS_MAIL, Equipment.SOLAR_AEGIS -> shield(c, sw, e)
            Equipment.SOULSTEALER_TOME, Equipment.ARCHMAGES_COWL -> tome(c, sw)
            Equipment.HOURGLASS_OF_STASIS -> hourglass(c, sw)
            Equipment.TRINITY_SIGIL -> trinity(c, sw)
            Equipment.GUARDIANS_HALO -> halo(c, sw)
            Equipment.MONARCHS_CROWN -> crown(c, sw)
        }
    }
}

private fun DrawScope.blade(c: Color, sw: Float, double: Boolean) {
    val w = size.width
    val h = size.height
    val p = Path().apply { moveTo(w / 2, 0f); lineTo(w * 0.62f, h * 0.7f); lineTo(w * 0.38f, h * 0.7f); close() }
    drawPath(p, c, style = Stroke(sw))
    drawLine(c, Offset(w * 0.2f, h * 0.72f), Offset(w * 0.8f, h * 0.72f), sw)
    drawLine(c, Offset(w / 2, h * 0.72f), Offset(w / 2, h), sw)
    if (double) drawCircle(c, w * 0.08f, Offset(w / 2, h * 0.38f))
}

private fun DrawScope.star(c: Color, sw: Float, points: Int) {
    val w = size.width
    val p = Path()
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) w / 2 else w / 4.4f
        val a = Math.toRadians(-90.0 + i * 180.0 / points)
        val x = (w / 2 + cos(a) * r).toFloat()
        val y = (size.height / 2 + sin(a) * r).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, c, style = Stroke(sw))
}

private fun DrawScope.boot(c: Color, sw: Float) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.3f, 0f); lineTo(w * 0.6f, 0f); lineTo(w * 0.6f, h * 0.6f)
        lineTo(w, h * 0.78f); lineTo(w, h); lineTo(w * 0.3f, h); close()
    }
    drawPath(p, c, style = Stroke(sw))
    drawLine(c, Offset(0f, h * 0.3f), Offset(w * 0.22f, h * 0.3f), sw)
    drawLine(c, Offset(0f, h * 0.5f), Offset(w * 0.22f, h * 0.5f), sw)
}

private fun DrawScope.shield(c: Color, sw: Float, e: Equipment) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w / 2, 0f); lineTo(w, h * 0.18f); lineTo(w * 0.9f, h * 0.65f)
        lineTo(w / 2, h); lineTo(w * 0.1f, h * 0.65f); lineTo(0f, h * 0.18f); close()
    }
    drawPath(p, c, style = Stroke(sw))
    when (e) {
        Equipment.SOLAR_AEGIS -> drawCircle(c, w * 0.16f, Offset(w / 2, h * 0.45f), style = Stroke(sw))
        Equipment.THORN_PLATE -> for (i in 0 until 3) {
            val x = w * (0.3f + i * 0.2f)
            drawLine(c, Offset(x, h * 0.3f), Offset(x, h * 0.62f), sw)
        }
        else -> drawLine(c, Offset(w / 2, h * 0.18f), Offset(w / 2, h * 0.82f), sw)
    }
}

private fun DrawScope.tome(c: Color, sw: Float) {
    val w = size.width
    val h = size.height
    drawRect(c, Offset(w * 0.1f, 0f), androidx.compose.ui.geometry.Size(w * 0.8f, h), style = Stroke(sw))
    drawLine(c, Offset(w * 0.25f, 0f), Offset(w * 0.25f, h), sw)
    drawCircle(c, w * 0.14f, Offset(w * 0.58f, h * 0.45f), style = Stroke(sw))
}

private fun DrawScope.hourglass(c: Color, sw: Float) {
    val w = size.width
    val h = size.height
    val p = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(0f, h); lineTo(w, h); close() }
    drawPath(p, c, style = Stroke(sw))
    drawLine(c, Offset(w * 0.3f, h * 0.88f), Offset(w * 0.7f, h * 0.88f), sw)
}

private fun DrawScope.trinity(c: Color, sw: Float) {
    val w = size.width
    val h = size.height
    val r = w * 0.24f
    drawCircle(c, r, Offset(w / 2, r), style = Stroke(sw))
    drawCircle(c, r, Offset(r, h - r), style = Stroke(sw))
    drawCircle(c, r, Offset(w - r, h - r), style = Stroke(sw))
}

private fun DrawScope.halo(c: Color, sw: Float) {
    val w = size.width
    val h = size.height
    drawOval(c, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, h * 0.35f), style = Stroke(sw))
    val p = Path().apply { moveTo(w * 0.15f, h * 0.55f); lineTo(w / 2, h); lineTo(w * 0.85f, h * 0.55f) }
    drawPath(p, c, style = Stroke(sw))
}

private fun DrawScope.crown(c: Color, sw: Float) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(0f, h * 0.85f); lineTo(0f, h * 0.25f); lineTo(w * 0.28f, h * 0.55f); lineTo(w / 2, 0f)
        lineTo(w * 0.72f, h * 0.55f); lineTo(w, h * 0.25f); lineTo(w, h * 0.85f); close()
    }
    drawPath(p, c, style = Stroke(sw))
    drawLine(c, Offset(0f, h), Offset(w, h), sw)
}
