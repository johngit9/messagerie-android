package fr.messagerie.unifiee.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Moteur

/** Catalogue d'émoticônes par catégorie (Unicode 14 au plus, pour qu'elles s'affichent sur tous les téléphones). */
object CatalogueEmojis {
    private fun l(s: String) = s.trim().split(Regex("\\s+"))

    val categories: List<Pair<String, List<String>>> = listOf(
        "Visages" to l("""
            😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔
            🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 😵‍💫 🤯 🤠 🥳 🥸 😎
            🤓 🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈
            👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊
        """),
        "Gestes" to l("""
            👍 👎 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 🫵 ✋ 🤚 🖐️ 🖖 👋 🫡 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅
            🤳 💪 🦾 👀 👁️ 👅 👄 🧠 🫂 🙋 🙋‍♂️ 🙋‍♀️ 🙇 🤦 🤦‍♂️ 🤦‍♀️ 🤷 🤷‍♂️ 🤷‍♀️ 🙆 🙅 💁 🙎 🙍 🧏
            🏃 🚶 💃 🕺 🧘 👪 👫 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 👮 👷 🧑‍⚕️ 🧑‍🍳 🧑‍🏫 🧑‍💻 🎅
        """),
        "Cœurs" to l("""
            ❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 ❤️‍🔥 ❤️‍🩹 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 💌 💋 💯 💢 💥 💫 💦 💨
            🕳️ 💬 🗨️ 💭 💤 ✨ ⭐ 🌟 🔥
        """),
        "Fête" to l("""
            🎉 🎊 🥳 🎂 🍰 🧁 🎁 🎈 🎀 🎄 🎃 🎆 🎇 🧨 🪅 🥂 🍾 🍻 🍺 🍷 🥃 🍸 🍹 ☕ 🍵 🧃 🥤 🍼
            🍕 🍔 🍟 🌭 🥪 🌮 🌯 🥗 🍝 🍜 🍣 🍱 🥟 🍤 🥐 🥖 🧀 🥚 🍳 🥞 🥓 🍗 🍖 🥩 🍫 🍬 🍭 🍩 🍪 🍦 🍨
            🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥕 🌽 🥦 🍄 🥜 🌰
        """),
        "Nature" to l("""
            ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️ 🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 🌪️ 🌫️ 🌈 ☔ ⚡ 🌙 🌛 🌝 🌞 🌍 🪐 💧 🌊
            🌸 💐 🌹 🥀 🌺 🌻 🌼 🌷 🌱 🪴 🌲 🌳 🌴 🌵 🌾 🌿 ☘️ 🍀 🍁 🍂 🍃
            🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍
            🐙 🦀 🐠 🐟 🐬 🐳 🦈 🐊 🐘 🦒 🦓 🐪 🐄 🐑 🐐 🐓 🐇 🐿️ 🦔 🐾
        """),
        "Activités" to l("""
            ⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🏒 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 🏂 🏋️ 🚴 🏊 🏄 🧗 🏆 🥇 🥈 🥉 🏅 🎖️
            🎭 🎨 🎬 🎤 🎧 🎼 🎵 🎶 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🧩
            🚗 🚕 🚌 🚎 🏎️ 🚓 🚑 🚒 🚚 🚜 🛵 🏍️ 🚲 🛴 🚂 🚆 🚇 🚊 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🛳️ ⚓ ⛽ 🚦
            🏠 🏡 🏢 🏥 🏦 🏨 🏫 ⛪ 🏰 🗼 🗽 🏖️ 🏝️ 🏔️ ⛰️ 🏕️ 🗺️ 🧳
        """),
        "Objets" to l("""
            📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯️
            💶 💵 💳 🧾 💰 💎 ⚖️ 🔧 🔨 🛠️ 🔩 ⚙️ 🧰 🧲 🔑 🗝️ 🔒 🔓 🚪 🛏️ 🛋️ 🚿 🛁 🧹 🧺 🧻 🧼
            ✉️ 📧 📨 📩 📦 📫 📮 📝 ✏️ 🖊️ 🖋️ 📌 📍 📎 🖇️ 📏 📐 ✂️ 📁 📂 📅 📆 🗓️ 📇 📈 📉 📊 📋 📚 📖 🔖 🏷️
            💊 💉 🩹 🩺 🌡️ 👓 🕶️ 👔 👕 👖 👗 👙 👟 👠 👜 🎒 💼 ☂️ 🧸 🛒
        """),
        "Symboles" to l("""
            ✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ✖️ ➗ ❓ ❔ ❗ ❕ ‼️ ⁉️ ⚠️ 🚫 ⛔ 📛 🔞 ♻️ ⚜️ 🔰 ⭕ 🆗 🆕 🆒 🆓 🆘 🔝 🔜
            🔴 🟠 🟡 🟢 🔵 🟣 🟤 ⚫ ⚪ 🟥 🟧 🟨 🟩 🟦 🟪 🟫 ⬛ ⬜ 🔶 🔷 🔸 🔹 🔺 🔻 💠
            ➡️ ⬅️ ⬆️ ⬇️ ↗️ ↘️ ↙️ ↖️ ↔️ ↕️ 🔄 🔃 🔁 🔂 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔀
            🔔 🔕 📣 📢 🔇 🔈 🔉 🔊 ℹ️ ©️ ®️ ™️ #️⃣ *️⃣ 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟
            ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ ☮️ ☯️ ✝️ ☪️ 🕉️ ✡️ 🔯 🛐
            🇫🇷 🇪🇺 🇧🇪 🇨🇭 🇨🇦 🇬🇧 🇺🇸 🇩🇪 🇪🇸 🇮🇹 🇵🇹 🇳🇱 🇲🇦 🇩🇿 🇹🇳 🇯🇵 🇨🇳 🏳️ 🏴 🏁 🏳️‍🌈
        """),
    )

    /** Émoticônes choisies récemment (les plus récentes d'abord), gardées sur ce téléphone. */
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("emojis", Context.MODE_PRIVATE)
    fun recents(ctx: Context): List<String> =
        prefs(ctx).getString("recents", "").orEmpty().split(' ').filter { it.isNotEmpty() }
    fun memoriser(ctx: Context, e: String) {
        val l = (listOf(e) + recents(ctx).filter { it != e }).take(32)
        prefs(ctx).edit().putString("recents", l.joinToString(" ")).apply()
    }
}

/**
 * Grand choix d'émoticônes par catégorie (Récents en premier s'il y en a).
 * Sert à la saisie (insertion au curseur) et aux réactions. [actuelle] : émoticône déjà choisie, mise en évidence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleEmojis(titre: String, fermer: () -> Unit, actuelle: String? = null, teinte: Color = MaterialTheme.colorScheme.primary,
                  choisir: (String) -> Unit) {
    val ctx = LocalContext.current
    val recents = remember { CatalogueEmojis.recents(ctx) }
    val onglets = remember(recents) {
        (if (recents.isNotEmpty()) listOf("Récents" to recents) else emptyList()) + CatalogueEmojis.categories
    }
    var onglet by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = fermer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(titre, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            PrimaryScrollableTabRow(selectedTabIndex = onglet.coerceIn(0, onglets.size - 1), edgePadding = 12.dp,
                containerColor = Color.Transparent) {
                onglets.forEachIndexed { i, (nom, liste) ->
                    Tab(selected = i == onglet, onClick = { onglet = i },
                        modifier = Modifier.semantics { contentDescription = nom }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
                            Text(if (nom == "Récents") "🕘" else liste.first(), fontSize = 20.sp)
                            Text(nom, fontSize = 11.sp)
                        }
                    }
                }
            }
            val liste = onglets[onglet.coerceIn(0, onglets.size - 1)].second
            LazyVerticalGrid(GridCells.Adaptive(46.dp), Modifier.fillMaxWidth().height(320.dp).padding(horizontal = 8.dp)) {
                items(liste) { e ->
                    Box(
                        Modifier.size(46.dp)
                            .background(if (e == actuelle) teinte.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            .clickable { CatalogueEmojis.memoriser(Moteur.app, e); choisir(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 26.sp) }
                }
            }
        }
    }
}
