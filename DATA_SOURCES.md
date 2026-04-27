# Lumina — Data Sources

Technical reference for how each feature's data is obtained, processed, and displayed.

---

## Location

**Source:** Android `FusedLocationProviderClient` (GPS mode) or user-stored coordinates (manual mode)

| Mode | How it works |
|---|---|
| GPS | Single fix via `FusedLocationProviderClient.getCurrentLocation()`. Display name reverse-geocoded from Android `Geocoder`. Time zone inferred from the system (`ZoneId.systemDefault()`). |
| Manual | Lat/lon stored in DataStore preferences. Time zone estimated from longitude (15°/hour offset from UTC) via `estimateZoneFromLon()` — no network call required. |

All screens share a single `AppLocationRepository` instance. Location changes (mode switch or new manual city) propagate to all ViewModels via a `resolvedLocation` Flow.

---

## Home Screen

### Solar Events & Sky State

**Source:** On-device calculation only — no network call

**Algorithm:** NOAA Solar Calculator (`SolarCalculator.kt`)
- Reference: https://gml.noaa.gov/grad/solcalc/calcdetails.html
- Accuracy: sunrise/sunset within ~1 minute for latitudes ±60°

**Computed values per day:**
- Astronomical dawn/dusk (−18° solar altitude)
- Nautical dawn/dusk (−12°)
- Blue hour start/end (−6°)
- Sunrise / Sunset (−0.8333° refraction-corrected horizon)
- Golden hour start/end (+6°)
- Real-time solar altitude (used to drive the sky gradient and phase)

**Sky phase** is determined from solar altitude and whether it is morning or evening (longitude-based). The gradient palette interpolates smoothly between phase boundaries.

---

### Moon Data

**Source:** On-device calculation only — no network call

**Algorithm:** Meeus simplified ephemeris (`MoonCalculator.kt`)
- Reference: *Astronomical Algorithms*, Jean Meeus, Chapter 47
- Accuracy: moon position ~1°, rise/set ~5 minutes

**Computed values per day:**
- Moonrise / Moonset times
- Illumination percentage
- Moon phase name (New, Waxing Crescent, First Quarter, etc.)
- Distance classification (Perigee / Apogee / Average) based on mean distances (perigee ≈ 356,500 km, apogee ≈ 406,700 km)

---

### Tide Summary (home screen widget)

**Source:** Same data as the Tides screen — pulled from whichever NOAA station is currently active. See Tides section below.

Displayed fields: current water height (ft), rising/falling arrow, flood/ebb percentage, time to next high or low.

The flood/ebb percentage is height-based (not time-based): current height is interpolated between the surrounding H/L events to give a more accurate representation of actual water volume change.

---

## Tides Screen

**Source:** NOAA Tides & Currents CO-OPS API
- Base URL: `https://api.tidesandcurrents.noaa.gov/api/prod/datagetter`
- Authentication: none required

### Station Discovery

**Source:** NOAA Metadata API
- URL: `https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json`
- Two station types fetched and merged:
  - `type=waterlevels` — real-time sensor stations (have live water level data)
  - `type=tidepredictions` — harmonic prediction stations (no sensor, predictions only)
- Combined list is ~3,000 stations, deduplicated by station ID, cached in memory for the app session
- Nearest stations computed via Haversine distance; full-text search filters by name or state

### Tide Predictions (H/L events)

**Endpoint:** `product=predictions, interval=hilo, datum=MLLW, units=english`
- Returns high and low tide times and heights in feet above MLLW
- Fetched for a 7-day window starting from the selected date

### Tide Curve (6-minute samples)

**Endpoint:** `product=predictions, interval=6, datum=MLLW, units=english`
- Returns a sample every 6 minutes for the selected day
- Used to draw the smooth tide curve on the chart

> **Note:** Subordinate stations (not primary harmonic stations) only support `interval=hilo`. When 6-minute predictions are unavailable, a synthetic cosine curve is interpolated between the H/L events (`TidesViewModel.syntheticCurve()`).

### Verified Water Level (actual observations)

**Endpoint:** `product=water_level, datum=MLLW, units=english`
- Returns actual observed water levels (6-minute samples) for the past ~2 days
- Displayed as a second line on the chart when available (blue = predicted, green = verified)
- Falls back gracefully if the station has no real-time sensor or the date is in the future

### Tidal Currents

**Endpoint:** `product=currents_predictions, interval=MAX_SLACK, units=english`
- Returns max flood, max ebb, and slack water events for the day
- Velocity in knots; direction in degrees
- Only available at current prediction stations (a subset of tide stations)

---

## Weather Screen

**Source:** Open-Meteo Forecast API
- Base URL: `https://api.open-meteo.com/v1/forecast`
- Authentication: none required

### Single API call fetches all weather data:

| Parameter | Value |
|---|---|
| `current` | `temperature_2m, apparent_temperature, weather_code, surface_pressure, windspeed_10m, winddirection_10m, windgusts_10m` |
| `hourly` | `temperature_2m, weathercode, precipitation_probability, surface_pressure, windspeed_10m, winddirection_10m, windgusts_10m` |
| `daily` | `weathercode, temperature_2m_max, temperature_2m_min, precipitation_probability_max, windspeed_10m_max` |
| `temperature_unit` | `fahrenheit` |
| `windspeed_unit` | `kn` (knots) |
| `forecast_days` | `7` |
| `past_hours` | `24` (historical data for pressure trend computation) |

> **Note:** Open-Meteo uses `weather_code` (underscore) for the `current` block but `weathercode` (no underscore) for `hourly` and `daily`. Both are mapped via `@SerialName` in the DTOs.

### Current Conditions

Temperature (°F), apparent temperature, weather condition (WMO code → human-readable string via `wmoCondition()`), surface pressure (hPa), wind speed/gusts/direction (knots).

### Pressure Trend

Computed client-side from the 24-hour history included in the response (`past_hours=24`). Current pressure is compared to the value 3 hours ago. Maritime standard thresholds applied:

| Delta (hPa / 3h) | Trend |
|---|---|
| > +3.0 | Rising Fast |
| +1.0 to +3.0 | Rising |
| −1.0 to +1.0 | Steady |
| −3.0 to −1.0 | Falling |
| < −3.0 | Falling Fast |

### Pressure Chart (hourly)

Forward-looking only: filtered to `now → now + 24h`. The historical data fetched via `past_hours=24` is used only for trend computation and not shown on this chart. Time labels show actual clock times at 6-hour intervals.

### Pressure Chart (7-day)

One data point per day: the hourly entry whose time is closest to noon for each forecast day.

### 7-Day Forecast

Daily weather code, high/low temperature, precipitation probability, and max wind speed. WMO weather codes are mapped to conditions and icon glyphs via `wmoCondition()` and `wmoIcon()` in `WeatherModels.kt`.

### Wind Card (Weather screen)

**Current observation:** From the same Open-Meteo `current` block — speed, gusts, direction.

**24h hourly forecast:** From the Open-Meteo `hourly` block, filtered to the next 24 hours.

**7-day daily forecast:** Max wind speed from the Open-Meteo `daily` block. Direction from the noon hourly entry for each day.

---

## Tides Screen — Wind Card

**Source:** NOAA station met data (primary) with Open-Meteo as fallback

1. **NOAA observed wind** — `product=wind, units=english` from the active tide station. Only available if the station has a meteorological sensor. Returns 6-minute wind speed, direction, and gusts in knots. Labeled `NOAA_STATION` in the UI.
2. **Open-Meteo fallback** — if the NOAA station returns no wind data (no sensor, or network error), the `WindRepository` falls back to an Open-Meteo `v1/forecast` call (`forecast_days=2, windspeed_unit=kn`). Labeled `OPEN_METEO` in the UI.

---

## Charts Screen

**Source:** Map tile servers — no Lumina-specific API

| Layer | Base tiles | Overlay |
|---|---|---|
| Nautical | OpenStreetMap Mapnik (`tile.openstreetmap.org`) | OpenSeaMap seamark overlay (`tiles.openseamap.org/seamark`) — buoys, beacons, lights, wrecks, rocks |
| Ocean | ESRI World Ocean Base (`server.arcgisonline.com/…/Ocean/World_Ocean_Base`) — depth contours and bathymetric shading from GEBCO + NOAA | OpenSeaMap seamark overlay |
| Satellite | ESRI World Imagery (`server.arcgisonline.com/…/World_Imagery`) — true-colour satellite | OpenSeaMap seamark overlay |

> **ESRI tile format note:** ArcGIS REST services use `{z}/{y}/{x}` order (y before x), unlike the standard TMS `{z}/{x}/{y}`. This is handled explicitly in the tile source URL builders.

All tile providers are accessed via osmdroid's `MapTileProviderBasic`. Tiles are cached to disk (100 MB cap, trimmed to 80 MB) in `context.cacheDir/osmdroid`.

**Compass heading:** Android `TYPE_ROTATION_VECTOR` sensor — no network, updated at `SENSOR_DELAY_UI`.

**Device location on map:** Android `FusedLocationProviderClient` via osmdroid's `GpsMyLocationProvider`.

---

## Alarms

**Source:** Derived from the same solar and tide data computed above — no additional API calls.

- **Solar alarms:** Times computed by `SolarCalculator` for the current day. Scheduled via `AlarmManager.setExactAndAllowWhileIdle()`.
- **Tide alarms:** H/L event times fetched from NOAA (same call as the Tides screen). Each alarm fires a notification at the event time and optionally 10 minutes before.
- **Daily rescheduling:** `DailyAlarmWorker` (WorkManager) runs at midnight to recompute and reschedule all alarms for the new day.
- **Boot recovery:** `AlarmReceiver` handles `BOOT_COMPLETED` to restore alarms after a device restart.

---

## Summary Table

| Feature | Data source | Network? |
|---|---|---|
| Sun times & sky phase | NOAA Solar Calculator algorithm (on-device) | No |
| Moon phase & rise/set | Meeus ephemeris (on-device) | No |
| Tide predictions (H/L) | NOAA CO-OPS API | Yes |
| Tide curve (6-min) | NOAA CO-OPS API | Yes |
| Verified water level | NOAA CO-OPS API | Yes |
| Tidal currents | NOAA CO-OPS API | Yes |
| Station search | NOAA Metadata API | Yes |
| Current weather | Open-Meteo | Yes |
| Weather forecast (7-day) | Open-Meteo | Yes |
| Pressure chart & trend | Open-Meteo (with 24h history) | Yes |
| Wind (Weather screen) | Open-Meteo | Yes |
| Wind (Tides screen) | NOAA station met → Open-Meteo fallback | Yes |
| Chart tiles | OSM / OpenSeaMap / ESRI (cached) | Yes (tile fetch) |
| Compass heading | Rotation vector sensor | No |
| GPS location | FusedLocationProviderClient | No |
| Alarms | Derived from solar + tide data | No |
