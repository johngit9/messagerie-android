# Messagerie (Android)

Une messagerie unifiée pour Android : **SMS / RCS, WhatsApp, Telegram, LinkedIn et Element** dans une seule liste,
avec des **résumés par IA** et un filtre **« À traiter »** (les conversations où quelqu'un attend une réponse de vous).

L'appli ne parle qu'à **vos propres serveurs** :

| Service | Rôle |
|---|---|
| Serveur Matrix (Synapse, port 8008 par défaut) | passerelles mautrix : SMS/RCS (gmessages), WhatsApp, Telegram, LinkedIn |
| Relais Element (port 8787 par défaut) | historique Element déchiffré + correspondance des salons WhatsApp |
| Ollama (port 11434 par défaut) | résumés de conversation et amélioration des brouillons |

Aucune adresse, aucun jeton ni aucune donnée personnelle n'est inclus dans l'appli : tout se saisit dans l'écran **Réglages** au premier lancement.

## Installer

1. Téléchargez le fichier `.apk` de la dernière version dans les [Releases](../../releases/latest).
2. Sur le téléphone, ouvrez le fichier et autorisez l'installation depuis cette source.
3. Au premier lancement, renseignez :
   - l'adresse du serveur Matrix (plusieurs adresses possibles, séparées par des virgules : réseau local, VPN…),
     votre identifiant Matrix (`@utilisateur:domaine`) et votre jeton d'accès ;
   - l'adresse et le jeton du relais Element (facultatif) ;
   - l'adresse d'Ollama et le modèle (facultatif, par défaut `qwen3:8b`).

   Plus simple : dans **Réglages → Fichier de configuration → Importer…**, choisissez un fichier `.json` qui contient tout
   (modèle : [`messagerie-config.exemple.json`](messagerie-config.exemple.json) ; le `client.json` et le `element.json`
   de l'app Mac sont aussi acceptés). **Exporter…** enregistre vos réglages dans un tel fichier, pour les réimporter plus tard.
   Ce fichier contient vos jetons : gardez-le sur votre téléphone ou votre cloud privé, jamais dans ce dépôt.

Le téléphone doit pouvoir joindre ces serveurs (même réseau Wi-Fi ou VPN type Tailscale).

## Fonctions

- Liste unifiée, triée par dernier message, avec filtres **À traiter**, **Non lus**, **Toutes** et par messagerie ; Discussions / Groupes.
- Recherche plein texte dans tout l'historique archivé (noms, messages, résumés).
- Archive locale permanente (SQLite) : l'historique complet est recopié au premier passage, puis seules les nouveautés.
- Réception en temps réel (long-poll `/sync` Matrix et attente longue du relais), plus une vérification périodique de secours.
- Option « Rester à l'écoute en arrière-plan » (service au premier plan) pour recevoir les notifications appli fermée.
- Résumé IA de chaque conversation récente, réponse proposée (insérée seulement sur appui), réécriture du brouillon
  (Améliorer, Corriger les fautes, Plus court, Plus formel, Plus chaleureux).
- Notifications « réponse attendue », sourdine par conversation, indicateur « À traiter » manuel, « Marquer comme traitée ».
- Répondre en citant un message ; badge SMS / RCS.

## Discrétion

- **Aucun accusé de lecture n'est jamais envoyé.** Lu / non lu est conservé uniquement sur le téléphone.
  L'appli lit seulement les accusés posés depuis vos autres appareils pour marquer une conversation comme lue.
- **Rien ne part sans votre appui sur « Envoyer ».**
- Données stockées dans l'espace privé de l'appli ; sauvegarde Android désactivée.

## Compiler

Prérequis : Android SDK (plateforme 37, build-tools 37.0.0) et un JDK 17+.

```bash
./gradlew assembleRelease
```

L'APK est produit dans `app/build/outputs/apk/release/`. Chaque envoi sur `main` le compile avec GitHub Actions et le publie dans les Releases.

## Limites actuelles

- Les vocaux se lisent dans la bulle (appui sur ▶). Les photos s'affichent dans la conversation (plein écran au toucher) ; vidéos et documents s'ouvrent avec l'appli de votre choix.
- Réactions : celles des contacts s'affichent sous les messages ; un appui long sur un message permet de réagir. Votre réaction n'est transmise que sur votre appui, et seulement si « Transmettre mes réactions » est activé dans les Réglages (sinon elle reste sur le téléphone).
- Pas encore de transcription des vocaux ni de résumé d'un message isolé.
- L'ancien mode WhatsApp « lecture de la base WhatsApp Desktop » de la version Mac n'existe pas sur Android :
  WhatsApp passe uniquement par la passerelle mautrix-whatsapp.
