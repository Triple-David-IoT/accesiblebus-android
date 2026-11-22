# Stop data (PV-DATOS, A01)

Query checked on 6 November 2026. The service data has `FECHAACTUAL = 20260529`.

## Selection

Line **27**, direction **2** (Plaza de Castilla → Glorieta de Embajadores), itinerary `369441`, consecutive stops 21 to 24 of the `M6_ParadasPorItinerario` table. The API (A04) and the app (A10) share this selection through [`contracts/manifest.json`](../contracts/manifest.json).

| Trip sequence | Itinerary order | `CODIGOESTACION` | `DENOMINACION` (CRTM) | EMT stop code | EMT stop name |
| --- | --- | --- | --- | --- | --- |
| 1 | 21 | `456` | Pº PRADO-BANCO DE ESPAÑA | `76` | Banco de España |
| 2 | 22 | `459` | PZA.CANOVAS DEL CASTILLO | `77` | Neptuno |
| 3 | 23 | `460` | Pº PRADO-PZA.PLATERIA MTNEZ. | `79` | Museo del Prado-Jardín Botánico |
| 4 | 24 | `461` | Pº PRADO-ATOCHA | `81` | Prado-Atocha |

These stops replace the first selection on Paseo de la Castellana for three reasons:

- **They exist in both sources.** Two of the previous stops, EMT codes `56` and `52`, were reported as unavailable or nonexistent by EMT and were missing from its current list for line 27.
- **Several lines serve them.** Lines `001`, `14`, `27`, `37`, `45` and `C03` run through all four, which makes live testing easier.
- **They are close together.** The stops are 295 to 343 m apart, so all four fit well on the map.

All four have `SITUACION = S`, `GRADOACCESIBILIDAD = T` and no `FECHAFIN`. The meaning of those two values is not confirmed in the CRTM documentation, so the app must not present them as a guarantee of accessibility.

## Source

- Dataset: [Elementos de la Red de Autobuses Urbanos de Madrid: EMT](https://data-crtm.opendata.arcgis.com/datasets/8ef563f232c244ca9b3a5d2d6a3dc19b) (CRTM).
- Service: `https://services5.arcgis.com/UxADft6QPcvFyDU1/arcgis/rest/services/M6_Red/FeatureServer`
- Layer 0, `M6_Estaciones`: physical stops and coordinates.
- Table 3, `M6_ParadasPorItinerario`: stop order by line and direction. It was only used to choose the manifest; the app does not query it.

## Query

Parameters, which the HTTP client must build and encode:

| Parameter | Value |
| --- | --- |
| `where` | `CODIGOESTACION IN ('456','459','460','461')` |
| `outFields` | `CODIGOESTACION,DENOMINACION` |
| `outSR` | `4326` |
| `f` | `geojson` |

Full URL:

```text
https://services5.arcgis.com/UxADft6QPcvFyDU1/arcgis/rest/services/M6_Red/FeatureServer/0/query?where=CODIGOESTACION+IN+%28%27456%27%2C%27459%27%2C%27460%27%2C%27461%27%29&outFields=CODIGOESTACION%2CDENOMINACION&outSR=4326&f=geojson
```

The real response is in [`contracts/samples/stops.geojson`](../contracts/samples/stops.geojson).

## Result

- **Matches:** exactly four features.
- **Code type:** `CODIGOESTACION` is text, in both the layer schema and the GeoJSON. Use quotes in the filter.
- **Fields:** each feature has `properties.CODIGOESTACION`, `properties.DENOMINACION` and `geometry.coordinates`.
- **Coordinates:** in `[longitude, latitude]` order, in WGS84, over Madrid.
- **Response:** HTTP 200 with `Content-Type: application/json; charset=utf-8`, not `application/geo+json`.

## Rules for consumers

1. **Sort by the manifest.** The service returned `459, 460, 461, 456`, so the response order is not the sequence.
2. **Join by `CODIGOESTACION`.** Do not use `OBJECTID`, which this response does not include.
3. **Do not mix the codes.** `physicalStopCode` is the CRTM `CODIGOESTACION`. `emtStopCode` is the public EMT number (`CODIGOEMPRESA`). Sent to the EMT API, a CRTM code can return a different stop.
4. **Check that all four stops arrive** before accepting the download.

## Not verified

The query ran from a laptop. Downloading from the Android device, including its certificates, belongs to A10. The EMT stop names and codes were checked against its API once, close to midnight.
