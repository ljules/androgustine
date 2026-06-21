# AndroGustine Pilote – Documentation de Contexte pour Agents IA (Codex)

## Objectif du document

Cette documentation n'a pas pour but de décrire chaque ligne de code mais de permettre à un agent IA (Codex, ChatGPT, Gemini, etc.) de comprendre rapidement l'écosystème complet du projet Augustine et le rôle de l'application Android **AndroGustine Pilote**.

---

# 1. Écosystème global

Le système est composé de 4 applications distinctes qui communiquent autour d'un format JSON commun et d'une base temps réel Firebase Firestore.

## Vue d'ensemble

```text
┌─────────────────────┐
│  Sim‑Augustine Web  │
│ (Angular)           │
└──────────┬──────────┘
           │ Export JSON
           ▼
┌─────────────────────┐
│ AndroGustine Pilote │
│ (Android Kotlin)    │
└──────────┬──────────┘
           │ Publication temps réel
           ▼
     Firebase Firestore
           ▲
           │
 ┌─────────┴─────────┐
 │                   │
 ▼                   ▼
AndroGustine     AndroGustine Live
Copilote         (Angular Web)
(Android)        Lecture seule
```

---

# 2. Sim‑Augustine

## Rôle

Application Web Angular de simulation énergétique développée pour le projet Shell Eco‑Marathon.

Elle réalise :

- Import d'un circuit CSV
- Calcul de trajectoires énergétiques
- Simulation multi‑tours
- Calcul de Ghost Car
- Définition des stratégies de conduite
- Export des données nécessaires au guidage du pilote

## Sortie principale

Export d'un fichier JSON contenant :

- Description du circuit
- Points GPS
- Distance cumulée
- Stratégie du tour de départ
- Stratégie des tours de course
- Ghost Car
- Paramètres de simulation
- Nombre total de tours

Ce JSON constitue la source de vérité de la course.

---

# 3. AndroGustine Pilote

## Rôle

Application Android embarquée dans le véhicule.

C'est le coeur opérationnel du système.

Elle est utilisée par le pilote pendant la course.

## Responsabilités

### 1. Import du JSON Sim‑Augustine

L'utilisateur importe le fichier généré par Sim‑Augustine.

Principaux composants :

- SimAugustineImportReader
- SimAugustineImportRepository
- SimAugustineImportModels

Le JSON devient alors le circuit actif.

---

### 2. Guidage du pilote

Affichage :

- Circuit
- Position GPS
- Ghost Car
- Segments de stratégie
- Tour courant
- Chronomètre
- Delta par rapport à la Ghost Car

Composants principaux :

- CircuitView
- PilotScreen
- RaceViewModel

---

### 3. Acquisition GPS

Source : GPS du smartphone Android.

Composant :

- GpsService

Utilisation :

- vitesse instantanée
- position GPS
- progression sur le circuit
- détection des tours

---

### 4. Acquisition fréquence cardiaque

Capteur supporté :

- Polar H10

Communication :

- Bluetooth Low Energy (BLE)

Composant :

- HeartRateBleManager

---

### 5. Acquisition météo

Source : Open‑Meteo.

Données utilisées :

- température
- vitesse du vent
- probabilité de pluie

Composant :

- WeatherService

---

### 6. Journalisation CSV

Toutes les données importantes sont enregistrées localement.

Composant :

- SessionCsvLogger

Contenu typique :

- GPS
- vitesse
- chrono
- tours
- météo
- fréquence cardiaque
- ghost car
- stratégie active

---

### 7. Publication Firestore

Mission principale de l'application.

Le pilote publie l'ensemble des données de télémétrie dans Firebase Firestore.

Composant central :

- TelemetryFirestoreRepository

Données publiées :

- sessionId
- vitesse
- latitude
- longitude
- temps de course
- tour courant
- nombre de tours
- ghost car
- météo
- fréquence cardiaque
- stratégie active
- circuit
- segments de stratégie

---

# 4. Firebase Firestore

## Rôle

Bus de communication temps réel.

Toutes les autres applications consomment les données publiées par AndroGustine Pilote.

Le pilote est la source de données principale.

---

# 5. AndroGustine Copilote

## Rôle

Application Android utilisée par l'équipe dans les stands.

Elle lit les données Firestore publiées par le pilote.

## Fonctionnalités

### Lecture temps réel

Affichage :

- position véhicule
- vitesse
- progression
- ghost car
- météo
- fréquence cardiaque

### Cartographie

Deux modes :

- Circuit simplifié
- OpenStreetMap

### Envoi des consignes

Le copilote écrit dans Firestore :

- accélérer
- maintenir
- ralentir

et des états de course :

- course normale
- ne pas doubler
- arrêt
- stands

Ces informations sont ensuite lues par AndroGustine Pilote.

---

# 6. AndroGustine Live

## Rôle

Application Web Angular.

Destinée aux observateurs.

Lecture seule.

## Fonctionnalités

- consultation de la position du véhicule
- consultation de la vitesse
- consultation des stratégies
- visualisation du ghost car
- affichage des indicateurs de course

Aucune écriture dans Firestore.

---

# 7. Architecture logique des données

## Flux descendant (préparation)

Sim‑Augustine
→ JSON
→ AndroGustine Pilote

## Flux temps réel (course)

AndroGustine Pilote
→ Firestore

## Flux supervision

Firestore
→ AndroGustine Copilote

Firestore
→ AndroGustine Live

## Flux consignes

AndroGustine Copilote
→ Firestore
→ AndroGustine Pilote

---

# 8. Structure du projet Android

## Packages principaux

### data/

Contient :

- GPS
- BLE
- météo
- Firestore
- import JSON
- journalisation CSV

### model/

Modèles métier.

Exemples :

- RaceState
- Lap
- PilotUiState

### ui/

Interface Compose.

Principaux écrans :

- PilotScreen
- CircuitView

### viewmodel/

Logique applicative.

Composant central :

- RaceViewModel

---

# 9. Point d'entrée principal

## MainActivity

Responsable de :

- démarrage de l'application
- création de l'interface Compose
- injection du ViewModel

---

# 10. RaceViewModel : coeur du système

Le RaceViewModel orchestre pratiquement tout le comportement de l'application.

Il coordonne :

- GPS
- BLE
- météo
- chronométrage
- import JSON
- gestion des tours
- ghost car
- publication Firestore
- réception des consignes du copilote

Pour toute évolution importante du projet, l'analyse doit généralement commencer par cette classe.

---

# 11. Recommandations pour un agent IA

Lors d'une analyse ou d'une modification :

1. Commencer par RaceViewModel.
2. Identifier si la donnée provient :
   - du JSON Sim‑Augustine,
   - du GPS,
   - du BLE,
   - de la météo,
   - ou de Firestore.
3. Vérifier si la modification impacte :
   - AndroGustine Pilote,
   - AndroGustine Copilote,
   - AndroGustine Live,
   - Sim‑Augustine.
4. Préserver la compatibilité du format JSON exporté par Sim‑Augustine.
5. Préserver la compatibilité des documents Firestore existants.

---

Document généré pour faciliter la reprise et l'analyse du projet AndroGustine par des agents IA.
