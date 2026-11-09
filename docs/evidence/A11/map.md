# A11: stops on the map

Pixel 8 emulator, default debug build. "Ver mapa" on the Stops screen.

1. **With network** (`01-map-online.png`): OpenStreetMap tiles through osmdroid, with the four
   stops along Paseo del Prado and the map framed around them automatically. No location
   permission and no own position.
2. **Marker** (`02-marker-info.png`): a first tap shows the sequence, CRTM name and code
   ("2. PZA.CANOVAS DEL CASTILLO, Parada 459"); a second tap opens Confirm for that visit.
3. **Airplane mode, tiles already seen** (`03-map-airplane-cached.png`): the map still shows
   the area, from the tile cache in the app's internal files (`files/osmdroid/tiles/cache.db`).
4. **Airplane mode, no tiles** (`04-map-airplane-no-tiles.png`): after deleting the tile cache,
   the background is empty but the markers are there, and "Ver lista" shows the same stops
   from the local copy. This is the case TM02 expects: before a demo, open the map once with
   network so the tiles are cached.

osmdroid uses the app package as user agent, as the OpenStreetMap tile policy asks.
