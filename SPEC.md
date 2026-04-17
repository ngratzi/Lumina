# Lumina — App Specification

**Platform:** Android (Samsung S24+, min SDK 31)  
**Version:** 1.0  
**Package:** `com.ngratzi.lumina`

---

## Overview

Lumina is a data-rich environmental awareness app. It surfaces solar events, lunar data, tidal information, tidal currents, and wind conditions for the user's location — with alarms for key moments.

The UI dynamically themes itself to match the actual sky state at the current time.

---

## Features

### 1. Solar Data

| Event | Definition |
|---|---|
| Astronomical dawn/dusk | Sun at -18° altitude |
| Nautical dawn/dusk | Sun at -12° |
| Blue hour | Sun at -6° to -0.833° |
| Sunrise / Sunset | Sun at -0.833° (refraction corrected) |
| Golden hour | Sun at -0.833° to +6° |
| Solar noon | Sun at maximum altitude |

**Displayed:** exact times, durations, countdown to next event.

---

### 2. Lunar Data

- Phase name + emoji glyph
- Illumination percentage
- Moonrise / transit / moonset
- Distance (km) — perigee/apogee labeled within 5%
- Days until next full moon and new moon

---

### 3. Tide Data

#### Data Source — NOAA Only

User maintains ≤5 saved local stations, all within their coastal region. NOAA Tides & Currents API covers this fully at no cost with no API key required.

| Endpoint | Purpose |
|---|---|
| `tidesandcurrents.noaa.gov/api/datagetter` | Water level predictions + verified observations |
| `tidesandcurrents.noaa.gov/mdapi/latest/webapi/stations.json` | Station search/discovery |
| `api.tidesandcurrents.noaa.gov/api/co-ops/` (CO-OPS v2) | Meteorological data (wind) |

**Caching:** cache each station's tide data for 24 hours. Solar/lunar is always offline. Full offline support for last-fetched data.

#### Tide Station Selection

Stations are managed separately from solar location:

1. **Auto:** nearest NOAA station to current GPS on first launch
2. **Search:** text search by station name, city, or NOAA station ID
3. **Saved list:** up to 5 stations with optional custom labels
4. **Active station:** one station active at a time; switch via Tides screen header

```kotlin
data class TideStation(
    val stationId: String,       // NOAA station ID (e.g. "8443970")
    val name: String,
    val lat: Double,
    val lon: Double,
    val customLabel: String? = null,
    val hasCurrentData: Boolean, // whether NOAA current station exists nearby
    val hasWindSensor: Boolean,  // whether station reports met data
)
```

#### Tide Display

**24h tide chart (Canvas-drawn):**
- Smooth spline curve of water level across the day
- **Two data series when available:**
  - Predicted (blue/white line) — always present
  - Verified/observed (amber line) — available for past ~2 days from NOAA verified product
- High and low tide markers: exact time + height
- Current water level indicator (animated pulse dot)
- Incoming / outgoing / slack labels at inflection points
- Time until next high/low shown persistently

**7-day tide list:**
- Each entry: date, day-of-week, time, height (ft), H/L type

**Units:** ft or m (user preference in Settings).

---

### 4. Tidal Current Data

NOAA maintains separate current stations (distinct from water level stations). Many coastal areas have associated current stations.

**Displayed when a current station exists within ~5 miles of active tide station:**
- Current velocity (knots)
- Current direction (degrees + compass label: N, NNE, NE, etc.)
- Flood / ebb / slack state
- Max flood and max ebb times for the day
- Slack water times (useful for diving, kayaking, fishing)

**On the tide chart:** optional overlay toggle showing current velocity as a secondary axis (bar chart beneath the tide curve, colored blue for flood, amber for ebb).

```kotlin
data class TidalCurrent(
    val time: ZonedDateTime,
    val velocityKnots: Double,
    val directionDeg: Double,
    val state: CurrentState,     // FLOOD | EBB | SLACK
)
```

If no nearby current station exists, this section is hidden — no placeholder or stub.

---

### 5. Wind Data

Source: NOAA meteorological observations from tide stations that have weather sensors. For stations without sensors, fall back to Open-Meteo forecast API (free, no key).

**Displayed on Tides screen, below the tide chart:**

| Field | Source |
|---|---|
| Current wind speed (knots / mph) | NOAA met observation or Open-Meteo |
| Current wind direction (degrees + compass + arrow indicator) | Same |
| Gust speed | Same |
| Beaufort scale description | Derived |
| Wind forecast (next 12h) | Open-Meteo (always; used as forecast even when NOAA obs available) |

**Wind mini-chart (Canvas):**
- 12-hour bar chart of wind speed
- Gust overlay (lighter bar on top)
- Direction arrows at each hour tick
- Color bands: calm (green) → moderate (yellow) → strong (orange) → gale (red)

**Beaufort scale labels used in UI:**

| Force | Speed (knots) | Label |
|---|---|---|
| 0 | < 1 | Calm |
| 1–2 | 1–6 | Light air / breeze |
| 3 | 7–10 | Gentle breeze |
| 4 | 11–16 | Moderate breeze |
| 5 | 17–21 | Fresh breeze |
| 6 | 22–27 | Strong breeze |
| 7–8 | 28–40 | Near gale / Gale |
| 9+ | > 41 | Severe / Storm |

```kotlin
data class WindObservation(
    val time: ZonedDateTime,
    val speedKnots: Double,
    val gustKnots: Double?,
    val directionDeg: Double,
    val beaufortForce: Int,
    val beaufortLabel: String,
    val source: WindSource,      // NOAA_STATION | OPEN_METEO
)
```

---

### 6. Alarms & Notifications

Users set per-event alarms with an optional lead time offset.

| Event | Default offset |
|---|---|
| Astronomical dawn | on time |
| Blue hour (morning) | on time |
| Golden hour (morning) | -15 min |
| Sunrise | on time |
| Golden hour (evening) | on time |
| Sunset | on time |
| Blue hour (evening) | on time |
| High tide | -30 min |
| Low tide | -30 min |
| Slack water (flood→ebb) | on time |
| Slack water (ebb→flood) | on time |
| Moonrise | on time |

**Implementation:**
- `AlarmManager.setExactAndAllowWhileIdle` for precision
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` permission (Android 12+)
- Notification payload: event name, exact time, height or phase (for tides/moon), countdown, deep-link to relevant screen
- Alarms recalculated daily at midnight via `WorkManager`
- Boot receiver re-registers alarms after device restart

---

---

## Screens

### Home
Primary view. Dynamically themed sky gradient. Shows:
- Location name + live clock
- Current sky phase label + countdown to next solar event
- Day timeline (horizontal, color-coded segments, current time marker)
- Sun data card (rise/set/transit + golden/blue hour windows)
- Moon data card (phase, illumination, rise/set, distance)
- Morning and evening hour windows (4 time blocks)

### Tides
Shows:
- Active station name + distance from GPS + switch button
- 24h tide chart (predicted + verified overlay)
- Wind mini-chart
- Tidal current card (only shown if current station available)
- Today's H/L tide list
- 7-day tide list

### Alarms
- Per-event alarm rows (toggle + offset setting)
- Solar events group / Tide events group / Lunar events group
- Notification preview per event type

### Settings
- Solar location (GPS or manual city search)
- Active tide station (from saved list)
- Manage saved stations (add/remove/reorder, max 5)
- Units: 12h/24h, ft/m, knots/mph
- Verified tide data: toggle whether to show observed overlay (on by default)
- Tidal current overlay on chart: toggle (on by default)

---

## Dynamic Sky Theme

Background, card surfaces, and text colors interpolate continuously based on current solar altitude:

| Phase | Sun Altitude | Background Gradient (zenith → horizon) |
|---|---|---|
| Night | < -18° | `#000008 → #010115 → #020220` |
| Astronomical twilight | -18° to -12° | `#020218 → #04043A` |
| Nautical twilight | -12° to -6° | `#060648 → #0C0C62` |
| Blue hour | -6° to 0° | `#0D1B5E → #1A3A8F → #2952A3` |
| Golden hour (morning) | 0° to 6° | `#4A1500 → #9B3800 → #D46000` |
| Daylight | > 6° | `#0A4A7A → #1A7AB5 → #2EA5D5` |
| Golden hour (evening) | 6° to 0° | `#5C1800 → #A83C00 → #E07000` |
| Blue hour (evening) | 0° to -6° | `#0F1E6A → #1C3E95 → #2455A8` |

Evening golden/blue hour uses slightly warmer/redder tones than morning. Smooth lerp between adjacent phases — no hard jumps.

Card surface, text colors, and accent colors all derived from the active phase palette.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Navigation | Navigation Compose |
| Local storage | Room (alarms, saved stations) + DataStore (preferences) |
| Location | FusedLocationProviderClient |
| Alarms | AlarmManager + WorkManager |
| Notifications | NotificationManager (channel: `lumina_events`) |
| Solar math | Custom (NOAA/Meeus algorithm, no library) |
| Lunar math | Meeus simplified ephemeris |
| Tide API | NOAA Tides & Currents REST API (free, no key) |
| Current API | NOAA CO-OPS Current Predictions (free, no key) |
| Wind (observed) | NOAA meteorological data at tide stations |
| Wind (forecast) | Open-Meteo API (free, no key) |
| HTTP | Retrofit + OkHttp |
| JSON | Kotlinx Serialization |
| Charts | Canvas-drawn (Compose Canvas, no external chart library) |

---

## API Endpoints Reference

```
# NOAA water level predictions
GET https://api.tidesandcurrents.noaa.gov/api/prod/datagetter
  ?station={id}&product=predictions&datum=MLLW
  &begin_date={YYYYMMDD}&end_date={YYYYMMDD}
  &interval=hilo&units=english&time_zone=lst_ldt&format=json

# NOAA verified water levels (observed, past ~2 days)
GET https://api.tidesandcurrents.noaa.gov/api/prod/datagetter
  ?station={id}&product=water_level&datum=MLLW
  &begin_date={YYYYMMDD}&end_date={YYYYMMDD}
  &units=english&time_zone=lst_ldt&format=json

# NOAA wind/met observations
GET https://api.tidesandcurrents.noaa.gov/api/prod/datagetter
  ?station={id}&product=wind
  &begin_date={YYYYMMDD}&end_date={YYYYMMDD}
  &units=english&time_zone=lst_ldt&format=json

# NOAA current predictions
GET https://api.tidesandcurrents.noaa.gov/api/prod/datagetter
  ?station={id}&product=currents_predictions
  &begin_date={YYYYMMDD}&end_date={YYYYMMDD}
  &interval=MAX_SLACK&units=english&time_zone=lst_ldt&format=json

# NOAA station search (find stations near coordinates)
GET https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json
  ?type=waterlevels&units=english

# Open-Meteo wind forecast (fallback / always used for 12h forecast)
GET https://api.open-meteo.com/v1/forecast
  ?latitude={lat}&longitude={lon}
  &hourly=windspeed_10m,winddirection_10m,windgusts_10m
  &windspeed_unit=kn&forecast_days=1
```

---

## Data Model Summary

```kotlin
data class SunTimes(
    val astronomicalDawn: ZonedDateTime?,
    val nauticalDawn: ZonedDateTime?,
    val blueHourStart: ZonedDateTime?,
    val sunrise: ZonedDateTime?,
    val goldenHourEnd: ZonedDateTime?,
    val solarNoon: ZonedDateTime,
    val goldenHourStart: ZonedDateTime?,
    val sunset: ZonedDateTime?,
    val blueHourEnd: ZonedDateTime?,
    val nauticalDusk: ZonedDateTime?,
    val astronomicalDusk: ZonedDateTime?,
)

data class MoonData(
    val phase: Double,
    val illumination: Double,
    val phaseName: String,
    val phaseEmoji: String,
    val moonrise: ZonedDateTime?,
    val moonTransit: ZonedDateTime?,
    val moonset: ZonedDateTime?,
    val distanceKm: Double,
    val daysToFullMoon: Int,
    val daysToNewMoon: Int,
)

data class TideStation(
    val stationId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val customLabel: String? = null,
    val hasCurrentData: Boolean,
    val hasWindSensor: Boolean,
)

data class TideEvent(
    val time: ZonedDateTime,
    val heightFt: Double,
    val type: TideType,              // HIGH | LOW
    val isVerified: Boolean,         // true = observed, false = predicted
)

data class TidalCurrent(
    val time: ZonedDateTime,
    val velocityKnots: Double,
    val directionDeg: Double,
    val state: CurrentState,         // FLOOD | EBB | SLACK
)

data class WindObservation(
    val time: ZonedDateTime,
    val speedKnots: Double,
    val gustKnots: Double?,
    val directionDeg: Double,
    val beaufortForce: Int,
    val beaufortLabel: String,
    val source: WindSource,          // NOAA_STATION | OPEN_METEO
)

data class AlarmConfig(
    val id: Int,
    val event: SolarEvent,
    val enabled: Boolean,
    val offsetMinutes: Int,          // negative = before event
)
```

---

## Permissions

```xml
<!-- Location -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Alarms -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## API Keys Required

| Service | Key Location | Notes |
|---|---|---|
| NOAA Tides & Currents | None | Fully free, no key |
| Open-Meteo | None | Fully free, no key |

---

## Open Questions / Resolved

| # | Question | Decision |
|---|---|---|
| 1 | API strategy for tides | NOAA only — user has ≤5 local stations, all US coastal |
| 2 | Predicted vs. verified data | Show both; verified as amber overlay when available (~past 48h) |
| 3 | Tidal current data | Include; hide section entirely if no current station nearby |
| 5 | Wind data source | NOAA met observations (when station has sensor) + Open-Meteo forecast always |
| - | Offline mode | Cache last-fetched tide/wind data; solar/lunar always offline |
| - | Google Calendar | Deferred — add in a later version |
| - | GCP setup docs | Document SHA-1 + OAuth client ID setup in README before implementation |
