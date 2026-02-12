# Safe Home Button

> **Your navigation helper** – Eine barrierefreie Android-App für intuitive Navigation mit einem schwebenden Button.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📱 Was macht die App?

Stell dir vor, deine Oma hätte Probleme, die Handy-Tasten am unteren Bildschirmrand zu erreichen oder zu finden. Mit diesem kleinen Punkt auf dem Bildschirm kommt sie mit dem Daumen immer wieder nach Hause – egal, wo sie gerade ist.

Weil dieser Punkt so praktisch ist, wurde er für die Ein-Daumen-Bedienung in zwei Modi erweitert:

### 🏠 Safe-Home-Modus (Standard – für maximale Sicherheit)
Ideal für Einsteiger oder Menschen mit motorischen Einschränkungen.
- **Alle Taps** → Führen sofort zum **Home-Screen**.
- **Design** → Quadratisch (angelehnt an die Android-Navigation).
- **Verschieben** → Nur durch **langes Drücken + Ziehen** möglich.
- **Sicherheit** → Verhindert versehentliches Verschieben des Buttons.

### 🧭 Navi-Modus (für fortgeschrittene Nutzer)
Macht das Handy komplett mit einem Daumen bedienbar.
- **1x tippen** → Zurück.
- **2x tippen** → Zur vorherigen App wechseln.
- **3x tippen** → Übersicht offener Apps.
- **Lang drücken** → Home-Screen.
- **Verschieben** → Button kann sofort gezogen werden.

## 🖼️ Screenshots

<p align="center">
  <img src="screenshots/Screenshot_20251103_212422_Safe Home Button.jpg" width="30%" alt="Hauptbildschirm" />
  <img src="screenshots/Screenshot_20251103_212431_Safe Home Button.jpg" width="30%" alt="Einstellungen" />
  <img src="screenshots/floating_dot.jpg" width="30%" alt="Safe Home Button in Aktion" />
</p>

<p align="center">
  <em>Hauptbildschirm • Einstellungen • Safe Home Button in Aktion</em>
</p>

## 🚀 Installation

### Voraussetzungen
- **Android 8.0** (API Level 26) oder höher.
- **Erforderliche Berechtigungen:**
  - *Über anderen Apps einblenden*: Für den schwebenden Punkt.
  - *Bedienungshilfe-Zugriff*: Um Navigationsbefehle (Home, Zurück) auszuführen.

### Schritt-für-Schritt
1. Lade die aktuelle **APK** unter [Releases](../../releases) herunter.
2. Installiere die APK auf deinem Gerät.
3. Öffne die App und folge dem Einrichtungsassistenten.
4. Erteile die angeforderten Berechtigungen in den Android-Einstellungen.

## 🎮 Bedienung

1. **Punkt einschalten**: Aktiviere den Schalter auf dem Hauptbildschirm.
2. **Modus wählen**: Wähle in den Einstellungen zwischen "Safe-Home" und "Navi".
3. **Punkt anpassen**:
   - **Farbe**: Wähle Farbton, Intensität und Helligkeit.
   - **Transparenz**: Stelle ein, wie stark der Punkt durchscheinen soll.
4. **Navigation Mode** (For Advanced Users):
- Tap once → Go back
- Tap twice → Previous app
- Tap 3 times → All open apps
- Long press → Go home
- Drag → Move button

*Note: You can enable "Long press to move" in settings for both modes to prevent accidental moves.*
   - Der Punkt "merkt" sich seine Position, auch wenn du das Handy drehst.
   - Er weicht automatisch der Tastatur aus ("Fahrstuhl-Effekt").

## 🛠️ Technische Details

### 🏗️ Architektur

**Safe Home Button** folgt strikt den Prinzipien der **Clean Architecture**. Dies garantiert Wartbarkeit, Testbarkeit und eine klare Trennung der Verantwortlichkeiten.

```text
Safe Home Button/
├── domain/                    # 🧠 Geschäftslogik (Rein Kotlin, kein Android)
│   ├── model/                 # Datenmodelle (DotPosition, OverlaySettings)
│   └── repository/            # Interfaces für Datenzugriff
├── data/                      # 💾 Daten-Schicht
│   ├── local/                 # SharedPreferences Implementierung
│   └── repository/            # Repository Implementierungen
├── service/                   # ⚙️ Android Services & Komponenten
│   └── overlay/
│       ├── OverlayService.kt       # Lifecycle & Orchestrierung
│       ├── KeyboardManager.kt      # Tastatur-Vermeidung
│       ├── PositionAnimator.kt     # Animationen
│       ├── OrientationHandler.kt   # Rotations-Logik
│       ├── GestureDetector.kt      # Touch-Events
│       └── ...
├── ui/                        # 🎨 Benutzeroberfläche (Activities)
├── util/                      # 🛠️ Hilfsklassen
└── di/                        # 💉 Dependency Injection (ServiceLocator)
```

### 🧩 Design-Prinzipien
- **Clean Architecture**: Strikte Trennung von Domain, Data und Presentation Layern.
- **Dependency Inversion**: Abhängigkeiten zeigen nach innen zur Domain-Logik.
- **Single Responsibility**: Jede Klasse hat genau eine Aufgabe (z.B. kümmert sich der `KeyboardManager` nur um die Tastatur).
- **Reactive Data Flow**: Nutzung von **Kotlin Flows** für Echtzeit-Updates der Einstellungen.

### 📱 Kern-Komponenten

#### OverlayService (Orchestrator)
Der zentrale Service verwaltet den Lifecycle. Durch Refactoring (Version 2.0.0) wurde er massiv entschlackt (Reduktion um ~30%), da Logik in Sub-Komponenten ausgelagert wurde.

#### KeyboardManager
Verhindert, dass der Button die Tastatur verdeckt.
- **Smart Margin**: Hält immer 1.5x Button-Durchmesser Abstand zur Tastatur.
- **Debouncing**: Verhindert Flackern bei schnellen Eingaben.

#### OrientationHandler
Sorgt für die korrekte Positionierung bei Bildschirmdrehung.
- **Zero Jump**: Der Punkt wird während der Drehung kurz ausgeblendet und erscheint sofort an der korrekten, mathematisch berechneten neuen Position.
- **Smart Detection**: 16ms Polling erkennt Dimensionsänderungen sofort.

### 🔧 Tech-Stack
- **Sprache**: Kotlin 1.9+
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36
- **Architektur**: MVVM + Clean Architecture
- **Concurrency**: Coroutines & Flows
- **Build**: Gradle Kotlin DSL
- **Testing**: JUnit 4 + Mockito

## 💻 Entwicklung

### Build-Anleitung

```bash
# Repository klonen
git clone https://github.com/Stephan-Heuscher/Safe-Home-Button.git
cd Safe-Home-Button

# Bauen und Abhängigkeiten laden
./gradlew build

# Release-Build erstellen (automatische Versionierung)
./gradlew assembleRelease

# Tests ausführen
./gradlew test
```

### Automatische Versionierung
Release-Builds aktualisieren automatisch die Version in `version.properties`:
- **Version Code**: +1 bei jedem Release.
- **Version Name**: Patch-Level wird erhöht (z.B. 1.1.0 → 1.1.1).

### ✨ Neu in Version 2.0.0
Dieses Major-Update brachte eine vollständige architektonische Überarbeitung:
- ✅ **Einführung Safe-Home-Modus**: Neuer Sicherheitsmodus mit Viereck-Design.
- ✅ **Refactoring**: Aufteilung des monolithischen Services in spezialisierte Komponenten.
- ✅ **UI-Update**: Kontextabhängige Anweisungen und verbesserte Einstellungen.
- ✅ **Performance**: Reaktive Datenströme und optimierte Ressourcennutzung.
- ✅ **Verbesserte Rand-Erkennung**: Button respektiert jetzt Statusleiste und Safe-Zones.

## ♿ Barrierefreiheit (Accessibility)

Die App orientiert sich an **WCAG 2.1 Level AA**:
- **Kontrast & Größe**: Hoher Kontrast und Touch-Targets >48dp.
- **Screen Reader**: Vollständige TalkBack-Unterstützung.
- **Sprache**: Einfache, verständliche Texte (A1-Niveau).

## 📋 Roadmap

### 🎯 Fokus
- **Migration zu Hilt**: Ablösung des manuellen ServiceLocators durch Hilt DI.
- **Test-Abdeckung**: Ausbau der Unit-Tests für alle neuen Komponenten.
- **WCAG 2.2**: Audit für den neuesten Barrierefreiheits-Standard.

### 💡 Ideen
- Wear OS Companion App.
- Benutzerdefinierte Gesten.
- Backup/Restore der Einstellungen.

## 🐛 Bekannte Einschränkungen
- **System-Einstellungen**: Ab Android 8.0 können Overlays aus Sicherheitsgründen nicht über System-Dialogen angezeigt werden.
- **Battery Saver**: Aggressive Energiesparmodi mancher Hersteller (z.B. Xiaomi, Samsung) können den Service beenden. Bitte "Keine Beschränkungen" für die App einstellen.

## 📄 Lizenz & Credits

**Lizenz:** MIT License – siehe [LICENSE](LICENSE).

**Autor:** [Stephan Heuscher](https://github.com/Stephan-Heuscher)

**Danksagungen:**
- Unterstützung durch Claude (Anthropic) bei Architektur-Fragen.
- Icons basierend auf Material Design.

---

**Hinweis:** Diese App ist ein technisches Hilfsmittel. Sie ersetzt keine medizinische Therapie. Bei motorischen Einschränkungen konsultieren Sie bitte Fachpersonal.

*Made with ❤️ for accessibility*