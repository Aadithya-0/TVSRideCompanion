# Direct navigation and map privacy

TVSRide can receive a destination shared from Google Maps, request a route
through a replaceable routing provider, and mirror the active maneuver to the
Raider cluster. Google Maps is used only as a destination source; the app does
not read its private account data.

## Network boundary

The direct-navigation mode requires network access for:

- following short Google Maps share-link redirects;
- route calculation;
- destination/map preview tiles, when enabled;
- surrounding map tiles for ride-history detail screens.

Ride history, BLE telemetry, and saved route points remain local. The routing
provider is intentionally abstracted so an ORS-compatible, Valhalla-compatible,
or self-hosted endpoint can be selected without changing the route-following
logic.

`HttpRoutingProvider` owns the OpenRouteService-compatible network boundary and
accepts an injected endpoint/API key. When `ORS_API_KEY` is absent, the app uses
the public OSRM demo endpoint as a no-key personal-use fallback. Both providers
receive the phone's origin and the shared destination; neither receives ride
history or BLE telemetry. Provider availability and HTTP failures are surfaced
in the route card rather than being presented as missing coordinates.

Ride-history detail uses the standard OpenStreetMap-compatible MAPNIK tile
source to show surrounding roads and a start pin. This is an explicit network
boundary: the tile server sees the tiles viewed around the saved start point,
but the ride coordinates and history remain in the app. A production
distribution should replace this with a provider whose mobile-app terms,
attribution, and rate limits are appropriate; this personal build does not
silently upload ride data.

## Share flow

1. Share a Google Maps destination to TVSRide.
2. Parse coordinates from `geo:`, `q=`, `query=`, or normal Maps URL formats.
3. If the share is a `maps.app.goo.gl` short link, follow only validated Google
   Maps redirects and parse coordinates from the redirect URL before enabling
   Preview route.
4. Show the destination, route distance, ETA, and a small preview.
5. Start guidance only after the rider confirms.

## Safety controls

- Keep provider URLs and API keys out of source control.
- Restrict provider quotas and log failures without logging precise routes.
- Never include rider names, MAC addresses, or raw GPS traces in diagnostics.
- Keep rerouting bounded by distance/time thresholds to avoid request loops.
