package fr.messagerie.unifiee

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Archive permanente de tous les messages (toutes sources), avec recherche plein texte,
 * et état local (lu / non lu, à traiter, sourdine, résumés IA). Fichier privé de l'appli.
 */
class Archive(context: Context) : SQLiteOpenHelper(context, "archive.db", null, 3) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE conversations (cle TEXT PRIMARY KEY, source TEXT NOT NULL, ident TEXT NOT NULL,
            nom TEXT, est_groupe INTEGER DEFAULT 0, derniere INTEGER DEFAULT 0)""")
        db.execSQL("""CREATE TABLE messages (cle TEXT PRIMARY KEY, conv TEXT NOT NULL, date INTEGER NOT NULL,
            de_moi INTEGER, auteur TEXT, type TEXT, texte TEXT, media TEXT, duree REAL)""")
        db.execSQL("CREATE INDEX messages_conv_date ON messages(conv, date)")
        try {
            db.execSQL("CREATE VIRTUAL TABLE recherche USING fts4(cle, conv, contenu, notindexed=cle, notindexed=conv, tokenize=unicode61 \"remove_diacritics=1\")")
        } catch (e: Exception) {
            db.execSQL("CREATE VIRTUAL TABLE recherche USING fts4(cle, conv, contenu)")
        }
        db.execSQL("CREATE TABLE curseurs (nom TEXT PRIMARY KEY, valeur TEXT)")
        db.execSQL("""CREATE TABLE etats (cle TEXT PRIMARY KEY, lue INTEGER DEFAULT 0, forcee INTEGER DEFAULT 0,
            traitee INTEGER DEFAULT 0, notifiee INTEGER DEFAULT 0, manuel INTEGER DEFAULT 0, sourdine INTEGER DEFAULT 0)""")
        db.execSQL("""CREATE TABLE syntheses (cle TEXT PRIMARY KEY, resume TEXT, action INTEGER, reponse TEXT, pour INTEGER)""")
        creerReactions(db)
        creerAvatars(db)
    }

    private fun creerReactions(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS reactions (cle TEXT PRIMARY KEY, cible TEXT NOT NULL, conv TEXT NOT NULL,
            emoji TEXT NOT NULL, auteur TEXT, de_moi INTEGER DEFAULT 0)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS reactions_conv ON reactions(conv)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS mes_reactions (cible TEXT PRIMARY KEY, conv TEXT NOT NULL, emoji TEXT NOT NULL,
            salon TEXT, id_envoi TEXT)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, ancienne: Int, nouvelle: Int) {
        if (ancienne < 2) creerReactions(db)
        if (ancienne < 3) creerAvatars(db)
    }

    private inline fun <T> Cursor.lignes(f: (Cursor) -> T): List<T> = use { c ->
        val r = ArrayList<T>()
        while (c.moveToNext()) r.add(f(c))
        r
    }

    private fun Cursor.s(n: String): String? = getColumnIndex(n).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.l(n: String): Long = getColumnIndex(n).let { if (it < 0 || isNull(it)) 0 else getLong(it) }

    // MARK: Écriture

    /** Ajoute ou met à jour les conversations. `garderNoms` : n'écrase pas une conversation déjà connue. */
    @Synchronized
    fun enregistrerConversations(convs: List<Conversation>, garderNoms: Boolean = false) {
        if (convs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (c in convs) {
                val v = ContentValues().apply {
                    put("cle", c.cle); put("source", c.source.id); put("ident", c.jid)
                    put("nom", c.nom); put("est_groupe", if (c.estGroupe) 1 else 0)
                }
                if (db.insertWithOnConflict("conversations", null, v, SQLiteDatabase.CONFLICT_IGNORE) == -1L && !garderNoms) {
                    v.remove("cle"); v.remove("source"); v.remove("ident")
                    db.update("conversations", v, "cle = ?", arrayOf(c.cle))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun enregistrerMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val effaces = effaces()
            for (m in messages) {
                // Supprimé par vous dans Messagerie : ne revient pas à la synchro suivante
                if (m.cle in effaces.first) continue
                effaces.second[m.conv]?.let { jusqua -> if (m.date <= jusqua) continue }
                val v = ContentValues().apply {
                    put("cle", m.cle); put("conv", m.conv); put("date", m.date); put("de_moi", if (m.deMoi) 1 else 0)
                    put("auteur", m.auteur); put("type", m.type); put("texte", m.texte); put("media", m.media)
                    if (m.duree != null) put("duree", m.duree) else putNull("duree")
                }
                db.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                db.delete("recherche", "cle = ?", arrayOf(m.cle))
                if (m.texte.isNotEmpty()) {
                    db.insert("recherche", null, ContentValues().apply { put("cle", m.cle); put("conv", m.conv); put("contenu", m.texte) })
                }
                db.execSQL("UPDATE conversations SET derniere = MAX(derniere, ?) WHERE cle = ?", arrayOf<Any>(m.date, m.conv))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Échos provisoires d'Element (identifiant « ~… ») : doublons du vrai message, à effacer. */
    fun purgerEchosElement() {
        val cles = readableDatabase.rawQuery("SELECT cle FROM messages WHERE cle LIKE 'Element:~%'", null).lignes { it.getString(0) }
        supprimerMessages(cles)
    }

    // MARK: Suppression par l'utilisateur (dans Messagerie seulement, rien n'est supprimé sur les messageries)

    private var cacheEffaces: Pair<Set<String>, Map<String, Long>>? = null

    /** (messages supprimés, conversations vidées → jusqu'à quelle date). */
    private fun effaces(): Pair<Set<String>, Map<String, Long>> = cacheEffaces ?: run {
        val db = writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS supprimes (cle TEXT PRIMARY KEY, jusqua INTEGER NOT NULL DEFAULT 0)")
        val msgs = HashSet<String>(); val convs = HashMap<String, Long>()
        db.rawQuery("SELECT cle, jusqua FROM supprimes", null).lignes { it.getString(0) to it.getLong(1) }.forEach { (c, j) ->
            if (c.startsWith("conv:")) convs[c.removePrefix("conv:")] = j else msgs.add(c)
        }
        (msgs to convs).also { cacheEffaces = it }
    }

    /** Supprime des messages de Messagerie (et s'en souvient pour ne pas les reprendre). */
    @Synchronized
    fun effacerMessages(cles: List<String>) {
        if (cles.isEmpty()) return
        effaces()
        val convs = cles.mapNotNull { c -> readableDatabase.rawQuery("SELECT conv FROM messages WHERE cle = ?", arrayOf(c)).lignes { it.getString(0) }.firstOrNull() }.toSet()
        val db = writableDatabase
        cles.forEach { db.insertWithOnConflict("supprimes", null, ContentValues().apply { put("cle", it); put("jusqua", 0) }, SQLiteDatabase.CONFLICT_REPLACE) }
        cacheEffaces = null
        supprimerMessages(cles)
        convs.forEach { conv ->
            db.execSQL("UPDATE conversations SET derniere = COALESCE((SELECT MAX(date) FROM messages WHERE conv = ?), 0) WHERE cle = ?", arrayOf(conv, conv))
        }
    }

    /** Supprime toute une conversation de Messagerie ; elle réapparaît seulement si un nouveau message arrive. */
    @Synchronized
    fun effacerConversation(conv: String, jusqua: Long) {
        effaces()
        val db = writableDatabase
        db.insertWithOnConflict("supprimes", null, ContentValues().apply { put("cle", "conv:$conv"); put("jusqua", jusqua) }, SQLiteDatabase.CONFLICT_REPLACE)
        cacheEffaces = null
        val cles = db.rawQuery("SELECT cle FROM messages WHERE conv = ? AND date <= ?", arrayOf(conv, jusqua.toString())).lignes { it.getString(0) }
        supprimerMessages(cles)
        db.execSQL("UPDATE conversations SET derniere = COALESCE((SELECT MAX(date) FROM messages WHERE conv = ?), 0) WHERE cle = ?", arrayOf(conv, conv))
    }

    @Synchronized
    fun supprimerMessages(cles: List<String>) {
        if (cles.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (c in cles) {
                db.delete("messages", "cle = ?", arrayOf(c))
                db.delete("recherche", "cle = ?", arrayOf(c))
                db.delete("reactions", "cle = ? OR cible = ?", arrayOf(c, c))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun existantes(cles: List<String>): Set<String> {
        if (cles.isEmpty()) return emptySet()
        val r = HashSet<String>()
        for (lot in cles.chunked(500)) {
            val marques = lot.joinToString(",") { "?" }
            readableDatabase.rawQuery("SELECT cle FROM messages WHERE cle IN ($marques)", lot.toTypedArray())
                .lignes { it.getString(0) }.let { r.addAll(it) }
        }
        return r
    }

    /** Tous les curseurs dont le nom commence par ce préfixe (sans le préfixe). */
    fun curseurs(prefixe: String): Map<String, String> =
        readableDatabase.rawQuery("SELECT nom, valeur FROM curseurs WHERE nom LIKE ?", arrayOf("$prefixe%"))
            .lignes { it.getString(0).removePrefix(prefixe) to it.getString(1) }.toMap()

    fun curseur(nom: String): String? =
        readableDatabase.rawQuery("SELECT valeur FROM curseurs WHERE nom = ?", arrayOf(nom)).lignes { it.getString(0) }.firstOrNull()

    @Synchronized
    fun definirCurseur(nom: String, valeur: String?) {
        if (valeur == null) writableDatabase.delete("curseurs", "nom = ?", arrayOf(nom))
        else writableDatabase.insertWithOnConflict("curseurs", null,
            ContentValues().apply { put("nom", nom); put("valeur", valeur) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: Photos de contact

    private fun creerAvatars(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS avatars (conv TEXT PRIMARY KEY, mxc TEXT NOT NULL)")
    }

    @Synchronized
    fun definirAvatars(a: Map<String, String>) {
        if (a.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((conv, mxc) in a) db.insertWithOnConflict("avatars", null, ContentValues().apply { put("conv", conv); put("mxc", mxc) }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun avatar(conv: String): String? =
        readableDatabase.rawQuery("SELECT mxc FROM avatars WHERE conv = ?", arrayOf(conv)).lignes { it.getString(0) }.firstOrNull()

    // MARK: Transcriptions des vocaux (sur le téléphone seulement)

    private fun creerTranscriptions() = writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS transcriptions (cle TEXT PRIMARY KEY, texte TEXT NOT NULL)")

    fun transcriptions(): Map<String, String> {
        creerTranscriptions()
        return readableDatabase.rawQuery("SELECT cle, texte FROM transcriptions", null).lignes { it.getString(0) to it.getString(1) }.toMap()
    }

    @Synchronized
    fun enregistrerTranscription(cle: String, texte: String) {
        creerTranscriptions()
        writableDatabase.insertWithOnConflict("transcriptions", null, ContentValues().apply { put("cle", cle); put("texte", texte) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: Réactions

    @Synchronized
    fun enregistrerReactions(rs: List<Reaction>) {
        if (rs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in rs) db.insertWithOnConflict("reactions", null, ContentValues().apply {
                put("cle", r.cle); put("cible", r.cible); put("conv", r.conv); put("emoji", r.emoji)
                put("auteur", r.auteur); put("de_moi", if (r.deMoi) 1 else 0)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Réactions reçues d'une conversation, par message visé. */
    fun reactions(conv: String): Map<String, List<Reaction>> =
        readableDatabase.rawQuery("SELECT * FROM reactions WHERE conv = ?", arrayOf(conv)).lignes { c ->
            Reaction(c.s("cle") ?: "", c.s("cible") ?: "", c.s("conv") ?: "", c.s("emoji") ?: "", c.s("auteur") ?: "", c.l("de_moi") == 1L)
        }.groupBy { it.cible }

    /** Oublie une réaction retirée (clé complète « Source:id »). */
    @Synchronized
    fun oublierReactionCle(cle: String) { writableDatabase.delete("reactions", "cle = ?", arrayOf(cle)) }

    /** Oublie une réaction envoyée puis retirée (son identifiant d'événement). */
    @Synchronized
    fun oublierReaction(idEvenement: String) {
        writableDatabase.delete("reactions", "cle LIKE ?", arrayOf("%:" + idEvenement))
    }

    fun mesReactions(conv: String): Map<String, MaReaction> =
        readableDatabase.rawQuery("SELECT * FROM mes_reactions WHERE conv = ?", arrayOf(conv)).lignes { c ->
            MaReaction(c.s("cible") ?: "", c.s("conv") ?: "", c.s("emoji") ?: "", c.s("salon"), c.s("id_envoi"))
        }.associateBy { it.cible }

    fun maReaction(cible: String): MaReaction? =
        readableDatabase.rawQuery("SELECT * FROM mes_reactions WHERE cible = ?", arrayOf(cible)).lignes { c ->
            MaReaction(c.s("cible") ?: "", c.s("conv") ?: "", c.s("emoji") ?: "", c.s("salon"), c.s("id_envoi"))
        }.firstOrNull()

    @Synchronized
    fun definirMaReaction(cible: String, r: MaReaction?) {
        val db = writableDatabase
        if (r == null) { db.delete("mes_reactions", "cible = ?", arrayOf(cible)); return }
        db.insertWithOnConflict("mes_reactions", null, ContentValues().apply {
            put("cible", r.cible); put("conv", r.conv); put("emoji", r.emoji); put("salon", r.salon); put("id_envoi", r.idEnvoi)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // MARK: Lecture

    private fun message(c: Cursor) = Message(
        cle = c.s("cle") ?: "", conv = c.s("conv") ?: "", date = c.l("date"), deMoi = c.l("de_moi") == 1L,
        auteur = c.s("auteur") ?: "", texte = c.s("texte") ?: "", type = c.s("type") ?: "texte",
        media = c.s("media"), duree = c.getColumnIndex("duree").let { if (it < 0 || c.isNull(it)) null else c.getDouble(it) },
    )

    /** Toutes les conversations : `nbRecents` derniers messages si actives depuis `depuis`, sinon le dernier seul. */
    fun conversations(depuis: Long, nbRecents: Int = 60): List<Conversation> {
        val db = readableDatabase
        data class L(val cle: String, val src: Source, val ident: String, val nom: String, val groupe: Boolean, val derniere: Long)
        val convs = db.rawQuery("SELECT * FROM conversations WHERE derniere > 0 ORDER BY derniere DESC", null).lignes { c ->
            Source.depuis(c.s("source") ?: "")?.let { L(c.s("cle")!!, it, c.s("ident") ?: "", c.s("nom") ?: "?", c.l("est_groupe") == 1L, c.l("derniere")) }
        }.filterNotNull()
        // Dernier message de chaque conversation, en une requête.
        val derniers = HashMap<String, Message>()
        db.rawQuery("SELECT m.* FROM messages m JOIN conversations c ON c.cle = m.conv AND m.date = c.derniere", null)
            .lignes { message(it) }.forEach { derniers[it.conv] = it }
        return convs.mapNotNull { l ->
            val msgs = if (l.derniere >= depuis) messages(l.cle, null, nbRecents) else listOfNotNull(derniers[l.cle])
            if (msgs.isEmpty()) null else Conversation(l.cle, l.src, l.ident, l.nom, l.groupe, l.derniere, msgs)
        }
    }

    /** Historique d'une conversation (ordre chronologique), avant une date. */
    fun messages(conv: String, avant: Long?, limite: Int = 200): List<Message> =
        readableDatabase.rawQuery("SELECT * FROM messages WHERE conv = ? AND date < ? ORDER BY date DESC LIMIT ?",
            arrayOf(conv, (avant ?: Long.MAX_VALUE).toString(), limite.toString())).lignes { message(it) }.reversed()

    fun nombreMessages(conv: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE conv = ?", arrayOf(conv)).lignes { it.getInt(0) }.firstOrNull() ?: 0

    fun messageParCle(cle: String): Message? =
        readableDatabase.rawQuery("SELECT * FROM messages WHERE cle = ?", arrayOf(cle)).lignes { message(it) }.firstOrNull()

    /** Messages d'une source restés sur « Waiting for attachment … » (pièce jointe pas encore reçue). */
    fun messagesEnAttenteDePieceJointe(source: String, depuis: Long): List<Message> =
        readableDatabase.rawQuery("SELECT * FROM messages WHERE cle LIKE ? AND date > ? AND texte LIKE 'Waiting for attachment%' ORDER BY date DESC LIMIT 50",
            arrayOf("$source:%", depuis.toString())).lignes { message(it) }

    fun conversationDuMessage(cle: String): String? =
        readableDatabase.rawQuery("SELECT conv FROM messages WHERE cle = ?", arrayOf(cle)).lignes { it.getString(0) }.firstOrNull()

    /** Conversations contenant tous les mots cherchés (début de mot). */
    fun rechercher(texte: String): Set<String> {
        val mots = texte.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { "$it*" }
        if (mots.isEmpty()) return emptySet()
        return try {
            readableDatabase.rawQuery("SELECT DISTINCT conv FROM recherche WHERE contenu MATCH ? LIMIT 2000",
                arrayOf(mots.joinToString(" "))).lignes { it.getString(0) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    /** Messages d'une conversation contenant ces mots (du plus ancien au plus récent). */
    fun chercherDans(conv: String, texte: String, limite: Int = 300): List<Message> {
        val mots = texte.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { "$it*" }
        if (mots.isEmpty()) return emptyList()
        return try {
            readableDatabase.rawQuery("SELECT m.* FROM recherche r JOIN messages m ON m.cle = r.cle " +
                "WHERE r.contenu MATCH ? AND r.conv = ? ORDER BY m.date DESC LIMIT ?",
                arrayOf(mots.joinToString(" "), conv, limite.toString())).lignes { message(it) }.reversed()
        } catch (e: Exception) { emptyList() }
    }

    /**
     * Recherche globale : messages de toutes les conversations (du plus récent au plus ancien),
     * avec filtres facultatifs par messagerie, conversation, période et pièces jointes seulement.
     */
    fun chercherPartout(texte: String, source: Source?, convs: Set<String>?, depuis: Long?, piecesJointes: Boolean, limite: Int = 300): List<Message> {
        val mots = texte.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { "$it*" }
        if (mots.isEmpty() && !piecesJointes && convs == null) return emptyList()
        if (convs != null && convs.isEmpty()) return emptyList()
        val conds = ArrayList<String>(); val args = ArrayList<String>()
        if (mots.isNotEmpty()) { conds += "r.contenu MATCH ?"; args += mots.joinToString(" ") }
        if (source != null) { conds += "m.conv LIKE ?"; args += "${source.id}:%" }
        if (convs != null) { conds += "m.conv IN (" + convs.joinToString(",") { "?" } + ")"; args += convs }
        if (depuis != null) { conds += "m.date >= ?"; args += depuis.toString() }
        if (piecesJointes) conds += "m.type IN ('image','video','fichier','audio')"
        val depuisTable = if (mots.isNotEmpty()) "recherche r JOIN messages m ON m.cle = r.cle" else "messages m"
        args += limite.toString()
        return try {
            readableDatabase.rawQuery("SELECT m.* FROM $depuisTable WHERE ${conds.joinToString(" AND ")} ORDER BY m.date DESC LIMIT ?",
                args.toTypedArray()).lignes { message(it) }
        } catch (e: Exception) { emptyList() }
    }

    /** Nombre de messages d'une conversation depuis une date (pour charger l'historique jusqu'à un résultat). */
    fun nombreDepuis(conv: String, date: Long): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE conv = ? AND date >= ?", arrayOf(conv, date.toString()))
            .lignes { it.getInt(0) }.firstOrNull() ?: 0

    fun aDesConversations(s: Source): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM conversations WHERE source = ? LIMIT 1", arrayOf(s.id)).use { it.moveToFirst() }

    @Volatile private var stats: Pair<Long, Map<Source, Pair<Int, Int>>>? = null

    /** Comptages par messagerie (requête lourde : gardée 60 s). */
    fun statistiques(): Map<Source, Pair<Int, Int>> {
        stats?.let { (t, v) -> if (System.currentTimeMillis() - t < 60_000) return v }
        val v = calculerStatistiques()
        stats = System.currentTimeMillis() to v
        return v
    }

    private fun calculerStatistiques(): Map<Source, Pair<Int, Int>> {
        val r = HashMap<Source, Pair<Int, Int>>()
        readableDatabase.rawQuery("""SELECT c.source AS s, COUNT(DISTINCT c.cle) AS nc, COUNT(m.cle) AS nm
            FROM conversations c LEFT JOIN messages m ON m.conv = c.cle GROUP BY c.source""", null).lignes { c ->
            Source.depuis(c.s("s") ?: "")?.let { r[it] = c.l("nc").toInt() to c.l("nm").toInt() }
        }
        return r
    }

    // MARK: État local

    fun etats(): Map<String, EtatConv> =
        readableDatabase.rawQuery("SELECT * FROM etats", null).lignes { c ->
            c.s("cle")!! to EtatConv(c.l("lue"), c.l("forcee"), c.l("traitee"), c.l("notifiee"), c.l("manuel") == 1L, c.l("sourdine") == 1L)
        }.toMap()

    @Synchronized
    fun enregistrerEtat(cle: String, e: EtatConv) {
        writableDatabase.insertWithOnConflict("etats", null, ContentValues().apply {
            put("cle", cle); put("lue", e.lue); put("forcee", e.forcee); put("traitee", e.traitee); put("notifiee", e.notifiee)
            put("manuel", if (e.manuel) 1 else 0); put("sourdine", if (e.sourdine) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun syntheses(): Map<String, Synthese> =
        readableDatabase.rawQuery("SELECT * FROM syntheses", null).lignes { c ->
            c.s("cle")!! to Synthese(c.s("resume") ?: "", c.l("action") == 1L, c.s("reponse") ?: "", c.l("pour"))
        }.toMap()

    @Synchronized
    fun enregistrerSynthese(cle: String, s: Synthese?) {
        if (s == null) { writableDatabase.delete("syntheses", "cle = ?", arrayOf(cle)); return }
        writableDatabase.insertWithOnConflict("syntheses", null, ContentValues().apply {
            put("cle", cle); put("resume", s.resume); put("action", if (s.actionRequise) 1 else 0); put("reponse", s.reponse); put("pour", s.pourLeMessageDu)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
