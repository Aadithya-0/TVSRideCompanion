# Direct navigation and map privacy

TVSRide can receive a destination shared from Google Maps, request a route
through a replaceable routing provider, and mirror the active maneuver to the
Raider cluster. Google Maps is used only as a destination source; the app does
not read its private account data.

## Network boundary

The direct-navigation mode requires network access for:

- following short Google Maps share-link redirects;
- route calculation;
- destination/map preview tiles, when enabled.

Ride history, BLE telemetry, and saved route points remain local. The routing
provider is intentionally abstracted so an ORS-compatible, Valhalla-compatible,
or self-hosted endpoint can be selected without changing the route-following
logic.

`HttpRoutingProvider` owns the network boundary and accepts an injected
endpoint/API key. It does not ship a provider key or silently select a
third-party service, so the UI can validate a shared destination before routing
is enabled. The current build reads `ORS_API_KEY` from the build environment;
without it, share parsing and local history remain available but route requests
are rejected locally.

Do not use the public OpenStreetMap tile server as an unrestricted mobile tile
backend. OSM data is open, but the public tile service has capacity and caching
rules. Use a provider with mobile-app terms, a bounded cache, or self-hosted
tiles, and show required attribution.

## Share flow

1. Share a Google Maps destination to TVSRide.
2. Parse coordinates from `geo:`, `q=`, `query=`, or normal Maps URL formats.
3. If the share contains only a place name or short link, show a confirmation
   state rather than silently geocoding an uncertain location.
4. Show the destination, route distance, ETA, and a small preview.
5. Start guidance only after the rider confirms.

## Safety controls

- Keep provider URLs and API keys out of source control.
- Restrict provider quotas and log failures without logging precise routes.
- Never include rider names, MAC addresses, or raw GPS traces in diagnostics.
- Keep rerouting bounded by distance/time thresholds to avoid request loops.
