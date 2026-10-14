# EquinoxCP2 — CarPlay + apps custom pour Chevrolet Equinox EV

App Android Automotive (AAOS) personnelle de Dom pour sa **Chevrolet Equinox EV** (head unit GM gminfo, Android 12L / API 32+). Elle affiche **Apple CarPlay** sur l'écran de l'auto via l'adaptateur **Carlinkit CPC200-CCPA**. Par-dessus CarPlay, elle ajoute un panneau **« Applications »** avec des apps maison qu'on ne peut pas avoir dans CarPlay.

Fork personnel de Carlink. CarPlay seulement, pas d'Android Auto.

| | |
|---|---|
| Package | `com.thib5.carlink` |
| Play Console | app **EquinoxCP2**, piste **Tests internes**, Android Automotive OS seulement |
| Branche de travail | `claude/carlink-apk-fork-access-gwyx5e` (branche par défaut) |
| minSdk / targetSdk | 32 / 36 |
| UI | Kotlin + Jetpack Compose, style « glassmorphisme » à la Apple |

Les autos GM de production sont verrouillées : pas d'ADB ni de sideload. **L'installation passe uniquement par Google Play** (piste de test interne).

---

## Fonctionnement général

1. Le Carlinkit est branché en USB sur l'auto. L'iPhone se connecte au Carlinkit (sans fil).
2. L'app reçoit la vidéo H.264 de CarPlay et l'affiche dans une `SurfaceView` plein écran (overlay HWC, faible latence). Elle renvoie aussi le tactile multipoint.
3. Le bouton OEM de CarPlay (icône bleue en bas à gauche de CarPlay) s'appelle **« Applications »**. Il ouvre le panneau d'apps custom **par-dessus CarPlay, qui reste en direct**.
4. L'audio peut passer par l'adaptateur ou par le **Bluetooth** de l'auto. Dans les deux modes, l'adaptateur envoie quand même les métadonnées de la chanson en cours (titre, artiste, album, durée, position).

---

## Le panneau « Applications »

Le panneau est collé à droite de l'écran. CarPlay reste utilisable à gauche, et les touches en dehors du panneau passent à CarPlay.

- **Suit la zone sûre de CarPlay** : sous la barre d'état AAOS si elle est visible, et à l'intérieur des marges réglées par l'utilisateur (gauche, droite, haut, bas, en %).
- **L'écran GM a le bord droit en biais et les coins droits coupés.** Le panneau a donc de grands coins arrondis à droite (120 dp) et une grande marge intérieure à droite (76 dp). Le contenu ne doit jamais aller sous la courbe.
- **Barre verticale à gauche du panneau, toujours visible** : Menu, chaque app, Réglages, Plein écran, Fermer.
- **Plein écran par app** : le bouton ⤢ agrandit l'app courante (ou le menu) sur toute la largeur utile. Le choix est mémorisé **par app** dans `SharedPreferences("carlink_apps")`, clé `fullscreen`.
- **Largeur réglable** : 30 % à 70 % de la largeur utile, 47 % par défaut, minimum 520 dp. Se règle dans *Réglages → Panneau Applications*. Changer seulement la largeur ne redémarre pas la session CarPlay.
- **Réouverture intelligente** : si le panneau a été fermé il y a moins de 3 minutes, il rouvre la dernière app. Sinon, il ouvre le menu.
- Le menu est une grille de tuiles de taille fixe collées à gauche, près de la barre, pour laisser l'espace libre loin du bord en biais.

### Les apps

| App | Fichier | Ce qu'elle fait |
|---|---|---|
| **Paroles** | `AppLyrics.kt` | Paroles synchronisées de la chanson en cours via **LRCLIB** (`lrclib.net`), avec la ligne active surlignée et défilement automatique. Boutons ± 0,5 s pour décaler. Paroles non synchronisées en repli. **Texte 1,5× plus gros en plein écran.** |
| **Quiz musical** | `AppQuiz.kt` | Panneau opaque (cache le titre affiché par CarPlay). Bouton **Révélation** : titre, artiste, album et date de sortie via l'**API iTunes Search**. Pointage « Je l'avais » / « Raté », qui passe à la chanson suivante, plus un bouton « Passer ». |
| **Météo** | `AppWeather.kt` | Prévisions via **Open-Meteo**, avec la position **GPS de l'auto** (permissions de localisation, `uses-feature` non requises). Maintenant, prochaines heures, prochains jours. |
| **Appareils** | `ui/settings/PhonesTab.kt` (`columns = 2`) | Liste des téléphones connus de l'adaptateur, en 2 colonnes. Toucher un appareil bascule CarPlay dessus. Les cartes prennent 80 % de la largeur (600 dp max) pour éviter le bord en biais. |
| **Dessin** | `AppDraw.kt` | Dessin au doigt : 7 couleurs, 3 grosseurs, annuler, tout effacer avec confirmation. **Le dessin est sauvegardé** dans `filesDir/drawing.json` (écriture dans un fichier temporaire, puis renommage). La prochaine personne qui ouvre l'app voit le dessin, avec « Dessiné le … ». |
| **Réglages** | `ui/settings/SettingsPanel.kt` | Ouvre le tableau de bord de réglages de l'app. |

Une app **Caméra** a été essayée puis retirée (v162) : GM n'expose pas les caméras de l'auto aux apps tierces. `AppCamera.kt` est un fichier vide gardé comme note.

### Détails techniques utiles

- **`NowPlayingStore`** (`AppSupport.kt`) : `StateFlow` alimenté par `CarlinkManager` à chaque message `MEDIA_DATA` de l'adaptateur (titre, artiste, album, durée, position, horodatage, lecture en cours). Il est vidé à la déconnexion. Les apps Paroles et Quiz s'en servent, et `livePositionMs()` extrapole la position entre deux messages.
- **Chanson suivante** : `CarlinkManager.nextTrack()`.
  - En audio par l'adaptateur, il envoie la commande `NEXT` (204) à l'adaptateur.
  - En **audio Bluetooth**, cette commande est ignorée. L'app envoie plutôt une touche média système `KEYCODE_MEDIA_NEXT` via `AudioManager.dispatchMediaKeyEvent`, et l'auto la relaie au téléphone par AVRCP.
- **`AppSupport.kt`** contient aussi les composants partagés : `AppNet` (HTTP simple), `Modifier.appGlass` / `appTile` (effet verre), `AppColors`, `AppRoundButton`, `AppMessage`, `rememberTicker`.
- **Icônes** :
  - Lanceur du menu Chevrolet : `mipmap/ic_launcher_plain`, une seule PNG transparente du logo bleu. Le lanceur GM affichait l'icône adaptative avec un cercle noir.
  - Bouton OEM dans CarPlay : `res/raw/carlink_oem_icon.png`, envoyé à l'adaptateur sous `/etc/carlink_oem_<crc32>.png`, avec le libellé `oemIconLabel = Applications` (voir `MessageSerializer.kt`).
- **Réglages utilisateur** : `SharedPreferences("carlink_user_settings")`, géré par `CarlinkSettings.kt`. Contient les marges en % (max 15) et `appsPanelWidthPercent`.

---

## Carte des fichiers

```text
app/src/main/kotlin/com/carlink/
├── CarlinkManager.kt        # Session adaptateur : USB, protocole, vidéo, audio, médias, nextTrack()
├── MainActivity.kt          # Activité unique, zone sûre + marges, application des réglages
├── protocol/                # Protocole Carlinkit (MessageTypes, MessageSerializer, AdapterDriver)
├── media/  audio/  usb/     # MediaSession/Media3, audio, accès USB
└── ui/
    ├── MainScreen.kt        # Écran principal : CarPlay + panneau Applications + réglages
    ├── AppsPanel.kt         # Panneau Applications : barre, menu, plein écran, mémoire
    ├── AppSupport.kt        # NowPlayingStore, réseau, styles verre, composants communs
    ├── AppLyrics.kt         # Paroles (LRCLIB)
    ├── AppQuiz.kt           # Quiz musical (iTunes Search)
    ├── AppWeather.kt        # Météo (Open-Meteo + GPS)
    ├── AppDraw.kt           # Dessin persistant
    └── settings/
        ├── CarlinkSettings.kt   # Réglages persistants
        ├── SettingsPanel.kt     # Écran de réglages
        └── PhonesTab.kt         # Cartes d'appareils (réglages + app Appareils)
```

---

## Compiler et publier

1. **CI** (`.github/workflows/build-aab.yml`) : chaque push sur la branche de travail qui touche `app/**`, les fichiers Gradle ou le workflow lance `:app:bundleSideloadRelease`. Le résultat est l'artefact **`carlink-unsigned-aab`**, un AAB non signé.
2. **Signature** : l'AAB est signé **hors du repo** avec la clé d'upload Play (`jarsigner`, SHA256withRSA, certificat SHA-1 `63:2F:3C:…`). **La clé et ses mots de passe ne sont jamais dans le repo.** Seul `keystore.properties.example` y est.
3. **Play Console** : on téléverse l'AAB signé sur la piste **Tests internes** d'EquinoxCP2, puis *Enregistrer et publier*. L'auto le reçoit par le Play Store en environ une heure.
4. **Chaque version doit augmenter `versionCode`** dans `app/build.gradle.kts`.

Pour compiler en local : `./gradlew :app:bundleSideloadRelease`. JDK 21 et Android SDK 36 ou plus sont nécessaires.

---

## Limites connues

- Pas d'accès aux caméras de l'auto. GM ne les expose pas.
- Le bord droit de l'écran est en biais. Toute nouvelle UI dans le panneau doit garder une marge à droite.
- Les apps Paroles, Quiz et Météo ont besoin d'internet dans l'auto.
- Projet personnel, testé seulement sur l'Equinox EV de Dom avec un CPC200-CCPA.

## Crédits

Basé sur Carlink et sa lignée :
[Carplay](https://github.com/abuharsky/carplay) par Abuharsky ·
[Node-Carplay](https://github.com/rhysmorgan134/node-CarPlay) par Rhysmorgan134 ·
LIVI par f-io ·
PyCarplay par Electric-Monk.
