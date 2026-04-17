# Lumina

<img width="540" height="1170" alt="lumina" src="https://github.com/user-attachments/assets/672ed266-99a9-4cd0-8510-641096a32e85" />


A data-rich environmental awareness app for Android. Surfaces solar events, lunar data, tides, tidal currents, and wind conditions — with a UI that dynamically themes itself to match the actual sky state at the current time.

Built for Samsung S24+, min SDK 31.

## Features

- **Sky Dial** — circular 24-hour clock showing sun and moon positions on concentric rings, colored by sky phase (night → astro/nautical twilight → blue hour → golden hour → daylight)
- **Solar data** — astronomical/nautical/civil dawn & dusk, blue hour, golden hour, sunrise/sunset, solar noon with countdown to next event
- **Lunar data** — phase disc, illumination %, moonrise/transit/set, days to full/new moon, perigee/apogee
- **Tides** — 24h Canvas chart with predicted and verified (observed) curves, 7-day H/L event list, tailing tide indicator for high tides above a configurable threshold
- **Tidal currents** — velocity, direction, flood/ebb/slack state; hidden when no nearby NOAA current station exists
- **Wind** — current obs + 12h forecast mini-chart with Beaufort labels; sourced from NOAA met stations or Open-Meteo fallback
- **Alarms** — per-event alarms with configurable lead time for solar, lunar, and tide events

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Navigation | Navigation Compose |
| Local storage | Room + DataStore |
| Location | FusedLocationProviderClient |
| Alarms | AlarmManager + WorkManager |
| Solar/lunar math | Custom (Meeus algorithms, no library) |
| Tide/current/wind | NOAA Tides & Currents REST API (free, no key) |
| Wind forecast | Open-Meteo API (free, no key) |
| HTTP | Retrofit + OkHttp |
| JSON | Kotlinx Serialization |
| Charts | Compose Canvas (no external chart library) |

## API Keys

None required. NOAA and Open-Meteo are both free with no key.

## Building

1. Clone the repo
2. Open in Android Studio
3. Run on a physical device (location + alarms require real hardware)

`local.properties` is gitignored — Android Studio generates it automatically from your SDK path.

## Project Structure

```
app/src/main/java/com/ngratzi/lumina/
├── data/
│   ├── local/          # Room DAOs and entities
│   ├── model/          # Domain models
│   ├── remote/         # Retrofit services + DTOs
│   └── repository/     # Data access layer
├── di/                 # Hilt modules
├── domain/             # Solar and lunar calculators
├── service/            # AlarmManager, WorkManager, boot receiver
├── ui/
│   ├── home/           # SkyDial, SolarEventList
│   ├── tides/          # Tide chart, wind card, current card, event list
│   ├── alarms/
│   ├── settings/
│   ├── navigation/
│   └── theme/          # Dynamic sky palette
└── util/               # LocationHelper, TimeFormatter
```
