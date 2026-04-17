# Lumina — Claude Context

Marine/sailing environmental awareness app for Android. Surfaces solar events, lunar data, tides, tidal currents, wind, and weather — with push notification alarms for key moments. Dynamic sky-themed UI that changes color based on actual solar altitude.

**Target device:** Samsung S24+  
**Package:** `com.ngratzi.lumina`  
**Min SDK:** 31 (Android 12)  
**Target SDK:** 35

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Navigation | Navigation Compose |
| Local storage | Room (alarms, saved stations) + DataStore (preferences) |
| Location | FusedLocationProviderClient |
| Alarms | AlarmManager (`setExactAndAllowWhileIdle`) + WorkManager (daily reschedule) |
| Notifications | NotificationManager, channel `lumina_events` |
| Solar math | Custom (NOAA/Meeus algorithm — no library) |
| Lunar math | Meeus simplified ephemeris |
| Charts | Canvas-drawn (Compose Canvas — no external chart library) |
| HTTP | Retrofit + OkHttp + kotlinx.serialization |
| Maps | osmdroid |

---

## Data Sources

| Source | What it provides | Auth |
|---|---|---|
| NOAA Tides & Currents API | Water level predictions, verified observations, tidal currents, met (wind) at stations with sensors | None |
| NOAA Stations API | Station search/discovery | None |
| Open-Meteo | Wind forecast fallback + full weather (current, hourly, daily) | None |

---

## Navigation / Screens

Bottom nav tabs in order: **Home → Tides → Weather → Charts → Settings**

| Screen | Route | Key content |
|---|---|---|
| Home | `home` | Sky gradient hero, solar dials, sun card, moon card, solar event list |
| Tides | `tides` | Tide chart (predicted + verified), tidal current card, tide event list. No wind. |
| Weather | `weather` | Current conditions, wind card, 24h pressure sparkline + trend, 7-day forecast |
| Charts | `charts` | osmdroid map with 3 layers: Nautical, Ocean, Satellite |
| Settings | `settings` | Preferences, alarm toggles with offset, QA test notification buttons |

---

## Package Structure

```
com.ngratzi.lumina
├── data/
│   ├── local/          — Room: AlarmDao, TideStationDao, LuminaDatabase
│   ├── model/          — Domain models: SunTimes, MoonData, TideModels, WindModels,
│   │                     WeatherModels, AlarmConfig, SkyPhase
│   ├── remote/
│   │   ├── dto/        — API DTOs: NoaaTideDto, OpenMeteoWindDto, OpenMeteoWeatherDto, etc.
│   │   ├── NoaaApiService.kt
│   │   ├── NoaaStationApiService.kt
│   │   └── OpenMeteoApiService.kt
│   └── repository/     — AlarmRepository, TideRepository, SolarRepository,
│                         WeatherRepository, WindRepository, UserPreferencesRepository
├── di/
│   └── AppModule.kt    — Hilt module: Room, Retrofit (3 instances: noaa, noaaStation, openMeteo),
│                         DataStore
├── domain/
│   ├── SolarCalculator.kt
│   └── MoonCalculator.kt
├── service/
│   ├── AlarmScheduler.kt   — Schedules/cancels AlarmManager intents; fires test notifications
│   ├── AlarmReceiver.kt    — BroadcastReceiver: handles ACTION_SOLAR, ACTION_TIDE, BOOT_COMPLETED
│   └── DailyAlarmWorker.kt — WorkManager worker; reschedules all alarms at midnight
├── ui/
│   ├── navigation/NavGraph.kt
│   ├── theme/          — SkyTheme, SkyPalette, beaufortColor(), sky phase palettes
│   ├── home/           — HomeScreen, HomeViewModel, components (SkyDial, SkyHero, SolarEventList,
│   │                     SunCard, MoonCard, DayTimeline)
│   ├── tides/          — TidesScreen, TidesViewModel, components (TideChart, TideEventList,
│   │                     WindCard, CurrentCard)
│   ├── weather/        — WeatherScreen, WeatherViewModel
│   ├── charts/         — ChartsScreen, ChartsViewModel
│   ├── alarms/         — AlarmsScreen, AlarmsViewModel
│   └── settings/       — SettingsScreen, SettingsViewModel
└── util/
    ├── LocationHelper.kt   — FusedLocationProviderClient wrapper
    └── TimeFormatter.kt    — Time/speed display formatting
```

---

## Key Architectural Patterns

### Hilt DI
- Three separate Retrofit instances tagged `@Named("noaa")`, `@Named("noaaStation")`, `@Named("openMeteo")`
- `@HiltWorker` + `@AssistedInject` for DailyAlarmWorker
- All repos are `@Singleton`

### osmdroid (ChartsScreen)
**Critical pattern — do not revert:**  
All `TilesOverlay` instances are pre-created in `remember` blocks and reused. Never call `map.overlays.clear()` — it removes osmdroid's internal base-tiles overlay and corrupts the map. Layer switching only calls `remove`/`add` on managed overlays.

```kotlin
val openSeaMapOverlay = remember(context) { tilesOverlay(context, OpenSeaMapSource) }
// In applyLayer: map.overlays.remove(openSeaMapOverlay); ...; map.overlays.add(openSeaMapOverlay)
```

Three chart layers:
- **NAUTICAL** — OSM Mapnik + OpenSeaMap seamark overlay
- **OCEAN** — ESRI World Ocean Base (`server.arcgisonline.com/.../Ocean/World_Ocean_Base/MapServer/tile/{z}/{y}/{x}`) + OpenSeaMap. Note ESRI tile format is `{z}/{y}/{x}` (y before x).
- **SATELLITE** — ESRI World Imagery + OpenSeaMap

Depth soundings: no free global tile source has individual depth soundings. ESRI Ocean Base has depth contours/shading only. User will likely add a paid source (Navionics/C-MAP) as a future `ChartLayer` entry.

### Alarm Scheduling
- Two PendingIntents per event: `event.ordinal * 2` (10-min warning), `event.ordinal * 2 + 1` (at event)
- `AlarmScheduler.fireTestNotification()` for QA — bypasses AlarmManager, fires notification directly
- `AlarmsViewModel.setEnabled(event, true)` immediately calls `scheduleForToday()` (not just at midnight)
- `POST_NOTIFICATIONS` requested at runtime in SettingsScreen on Android 13+

### Weather / Pressure Trend
- Open-Meteo called with `past_hours=24` to get pressure history for sparkline
- Trend detection: compare current hPa to 3 hours ago. Maritime standard: >3 hPa/3h = rapid change
- `PressureTrend` enum: `RISING_FAST, RISING, STEADY, FALLING, FALLING_FAST`
- Units: `temperature_unit=fahrenheit`, `windspeed_unit=kn`

### Wind
- `WindCard` lives in `ui/tides/components/WindCard.kt` but is imported by both TidesScreen and WeatherScreen
- WeatherScreen builds `WindObservation` and `WindForecast` from `WeatherData` in `remember` blocks
- TidesScreen no longer shows wind (moved to Weather tab)

### Solar Event Ranges
- Solar events displayed as time ranges (e.g. "5:21 AM – 5:44 AM") using `SolarRow(endTime = ...)` optional param
- `SolarEventList` stacks Solar and Lunar sections vertically (not side-by-side) — required for range strings to fit

### HomeViewModel — Next Event
- When all today's events have passed, `nextEvent()` looks at tomorrow's sun times
- Uses `SolarCalculator.getSunTimes(now.toLocalDate().plusDays(1), ...)` to avoid blank state at night

### Synthetic Tide Curve
- Subordinate NOAA stations only support `interval=hilo` (no 6-min predictions)
- `TidesViewModel.syntheticCurve()` builds a cosine-interpolated curve from H/L events as fallback

---

## Sky Theme

`LocalSkyTheme.current.palette` provides `SkyPalette` everywhere. Key colors:

```kotlin
interface SkyPalette {
    val gradientTop: Color
    val gradientBottom: Color
    val surfaceDim: Color
    val surfaceContainer: Color
    val onSurface: Color
    val onSurfaceVariant: Color
    val outlineColor: Color
    val accent: Color
}
```

`beaufortColor(force: Int): Color` in `ui/theme/Color.kt` — maps Beaufort force 0–12 to colors used in wind charts.

---

## Open-Meteo API Fields Reference

```
current:  temperature_2m, apparent_temperature, weather_code, surface_pressure,
          windspeed_10m, winddirection_10m, windgusts_10m
hourly:   temperature_2m, weathercode, precipitation_probability, surface_pressure,
          windspeed_10m, winddirection_10m, windgusts_10m
daily:    weathercode, temperature_2m_max, temperature_2m_min,
          precipitation_probability_max, windspeed_10m_max
```

Note: Open-Meteo uses `weather_code` (with underscore) for current, but `weathercode` (no underscore) for hourly/daily. Both are handled via `@SerialName` in `OpenMeteoWeatherDto`.

---

## WMO Weather Codes

`wmoCondition(code)` and `wmoIcon(code)` are top-level functions in `data/model/WeatherModels.kt`.
Key groups: 0=clear, 1-3=partly cloudy/overcast, 45/48=fog, 51-55=drizzle, 61-65=rain, 71-75=snow, 80-82=showers, 95/96/99=thunderstorm.

---

## What's Built

- [x] Home screen with dynamic sky theme, solar dials, sun/moon cards, event list with ranges
- [x] Tides screen with Canvas chart, synthetic curve fallback, tidal current card, station picker
- [x] Weather screen with current conditions, wind card, pressure sparkline, 7-day forecast
- [x] Charts screen with 3 osmdroid layers (Nautical, Ocean, Satellite)
- [x] Alarms screen with solar + tide alarm groups
- [x] Settings screen with tailing tide threshold slider, QA notification buttons
- [x] AlarmScheduler with dual notifications (10-min warning + at-event)
- [x] DailyAlarmWorker for midnight rescheduling + boot receiver

## Known Gaps / Future Work

- Paid chart tile source (Navionics/C-MAP) for depth soundings — architecture ready, just add new `ChartLayer` entry
- Settings toggles (24h time, metric units, wind mph) are wired to UI but not yet persisted/applied
- "Manage stations" row in Settings is a placeholder
- No caching layer on tide data yet (spec calls for 24h cache)
- Wind data still visible in both Tides (removed) and Weather — if regression appears, check TidesScreen for stale WindCard import
