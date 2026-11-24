# Messagerie unifiée — app Android

App Android (Kotlin + Jetpack Compose + Material 3) qui regroupe SMS/RCS, WhatsApp, Telegram, LinkedIn, Signal (via les passerelles Matrix d'un serveur Synapse) et Element (via un relais), avec résumés IA par Ollama.

## Règles absolues

- **Dépôt PUBLIC : aucune donnée personnelle** dans le code, les commits ou les fichiers : ni jeton, clé, adresse IP, nom d'hôte (Tailscale ou autre), numéro, contact ou message. Les adresses et jetons se saisissent dans l'app (Réglages) ou s'importent par fichier JSON (`messagerie-config.exemple.json` = modèle avec des valeurs fictives).
- Ne jamais committer `messagerie-config.json`, `*.jks`, `*.keystore` (déjà dans `.gitignore`).
- **Aucun envoi en test** : ne jamais appeler `POST /envoyer` du relais ni envoyer de message ou de réaction pour essayer. Un envoi part seulement sur un appui explicite de l'utilisateur.
- **Discrétion** : lu / non lu reste local. Ne jamais envoyer d'accusé de lecture public (`m.read`) ni d'indicateur de saisie (`/typing`). Exception : Element lu en PUBLIC depuis le 24/11 (choix d'Alain : `POST /lu` du relais, m.read + m.fully_read ; `/lu-prive` reste disponible). Coches Element : `GET /accuses?salon=` du relais. Lus reportés dans l'appli officielle par `read_markers` (m.read + m.fully_read) : WhatsApp toujours (la passerelle du serveur les transmet en « read-self », invisible des contacts) ; SMS sur option (désactivée par défaut, Réglages > Confidentialité, Google Messages) ; JAMAIS pour Telegram, Signal, LinkedIn. Présence : seulement le statut choisi par l'utilisateur dans Réglages > Confidentialité (hors ligne par défaut, `set_presence` des /sync = ce choix).
- Ne pas changer le package `fr.messagerie.unifiee` : sinon Android voit une autre appli (réglages perdus, pas de mise à jour par-dessus).
- Textes de l'interface, commentaires et messages de commit **en français**.

## Compilation et publication

- Pas de SDK Android en local dans les sessions cloud : on compile **par GitHub Actions** (`.github/workflows/apk.yml`).
- Chaque push sur `main` → `assembleRelease` → Release `v1.<numéro du run>` avec `Messagerie-v1.<n>.apk`. `versionCode` = `GITHUB_RUN_NUMBER` (la page Mise à jour de l'app compare ce numéro au tag).
- Signature : clé fixe dans les secrets `CLE_SIGNATURE_BASE64`, `CLE_SIGNATURE_MDP`, `CLE_SIGNATURE_ALIAS`. Le journal doit afficher « Signature : clé fixe du dépôt » ; sinon l'APK ne s'installera pas par-dessus.
- Versions : catalogue `gradle/libs.versions.toml` (AGP 9.4, Kotlin 2.2, Gradle 9.6, compileSdk 37, minSdk 26). R8 + réduction des ressources actifs en release.
- Les autres branches compilent sans publier de Release (vérification).
- Les modifications de `*.md` seules ne déclenchent pas de compilation.
- **Après chaque version, lancer le workflow « Tester le démarrage »** (`test-demarrage.yml`, `workflow_dispatch`) : il installe la dernière Release dans un émulateur et affiche le plantage éventuel. R8 casse ce qui passe par réflexion (ex. base Room de WorkManager) : ajouter les règles dans `app/proguard-rules.pro`.

## Architecture (`app/src/main/java/fr/messagerie/unifiee/`)

| Fichier | Rôle |
|---|---|
| `Moteur.kt` | Objet central : état de l'UI (`StateFlow<Etat>`), synchronisation (sources en parallèle), lu/non lu local, IA, envoi, réactions, médias |
| `Matrix.kt` | API client-serveur Matrix (HTTP direct) et `SyncPont` par passerelle mautrix |
| `Relais.kt` | Relais Element (Bearer) : `/etat`, `/messages`, `/lus`, `/media`, `/whatsapp/salons` |
| `Ollama.kt` | Résumés, réponses suggérées, amélioration du texte |
| `Archive.kt` | SQLite (WAL) : messages, recherche FTS, états, synthèses, réactions |
| `Config.kt` | Réglages (SharedPreferences), import/export JSON, historique des imports |
| `MiseAJour.kt` | Vérifie la dernière Release GitHub publique, télécharge et installe l'APK |
| `Teams.kt` | Teams en lecture seule : service du serveur (port 8789 par défaut, même jeton que le relais), `/teams/messages?apres=<seq>` |
| `Lecteur.kt` | Lecture des vocaux (MediaPlayer) |
| `SynchroArrierePlan.kt` | WorkManager : synchro toutes les 15 min appli fermée (réglage « Vérifier régulièrement ») ; sans « réception en arrière-plan », les boucles s'arrêtent 1 min après avoir quitté l'appli (`Moteur.auRepos`) |
| `ui/Medias.kt` | Photos (Coil, plein écran), vidéos et documents ouverts via FileProvider |
| `ui/Liste.kt`, `ui/Conversation.kt`, `ui/Reglages.kt`, `ui/Commun.kt` | Écrans Compose |
| `ui/VueModeles.kt` | ViewModel par écran : `ListeVM` (filtres, recherche hors fil d'affichage), `ConversationVM` (historique, réactions) |
| `MainActivity.kt` | Thème (palette des maquettes ou Material You en option, échelle `Affichage`) et Navigation Compose (`liste`, `conversation/{cle}`, `reglages`, `accueil`) avec transitions |

MVVM sans Hilt : `Moteur` (singleton initialisé dans `App`) reste la source de données ; les ViewModel s'y abonnent.

## Design

Maquettes de référence (4 écrans) : https://claude.ai/artifact/PfEasR5KmmXe4kfhnSKbNX — fond `#F6F7F9`, cartes blanches bordure `#ECEEF2` arrondi 16 dp, accent `#3B5BDB`, texte `#15171C` / `#5A606D`, badge « Réponse attendue » `#A04A0B` sur `#FDEEDC`. Icônes `material-icons-extended`, zones de toucher ≥ 44 dp.
