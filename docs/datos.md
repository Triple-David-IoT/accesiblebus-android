# Datos de paradas (PV-DATOS, A01)

Consulta comprobada el 5 de noviembre de 2026. Los datos del servicio tienen `FECHAACTUAL = 20260529`.

## Selección

Línea **27**, sentido **1** (Glorieta de Embajadores → Plaza de Castilla), itinerario `342046`, paradas consecutivas 16 a 19 de la tabla `M6_ParadasPorItinerario`. La selección compartida por la API (A04) y la app (A10) está en [`contracts/manifest.json`](../contracts/manifest.json).

| Secuencia del viaje | Orden en el itinerario | `CODIGOESTACION` | `DENOMINACION` | Código público EMT (`CODIGOEMPRESA`) |
| --- | --- | --- | --- | --- |
| 1 | 16 | `292` | PZA.DOCTOR MARAÑON | 56 |
| 2 | 17 | `293` | PZA.SAN JUAN DE LA CRUZ | 54 |
| 3 | 18 | `294` | Pº. CASTELLANA-JORGE MANRIQUE | 52 |
| 4 | 19 | `1045` | Pº CASTELLANA-Pº DE LA HABANA | 50 |

Se eligieron porque son consecutivas, tienen nombres distintos y las cuatro figuran con `SITUACION = S`, `GRADOACCESIBILIDAD = T` y sin `FECHAFIN`. El significado de esos dos valores no está comprobado en la documentación del CRTM, así que la app no debe presentarlos como una garantía de accesibilidad.

## Fuente

- Conjunto: [Elementos de la Red de Autobuses Urbanos de Madrid: EMT](https://data-crtm.opendata.arcgis.com/datasets/8ef563f232c244ca9b3a5d2d6a3dc19b) (CRTM).
- Servicio: `https://services5.arcgis.com/UxADft6QPcvFyDU1/arcgis/rest/services/M6_Red/FeatureServer`
- Capa 0, `M6_Estaciones`: paradas físicas y coordenadas.
- Tabla 3, `M6_ParadasPorItinerario`: orden de las paradas por línea y sentido. Solo se usó para elegir el manifest; la app no la consulta.

## Consulta

Parámetros, que el cliente HTTP debe construir y codificar:

| Parámetro | Valor |
| --- | --- |
| `where` | `CODIGOESTACION IN ('292','293','294','1045')` |
| `outFields` | `CODIGOESTACION,DENOMINACION` |
| `outSR` | `4326` |
| `f` | `geojson` |

URL completa:

```text
https://services5.arcgis.com/UxADft6QPcvFyDU1/arcgis/rest/services/M6_Red/FeatureServer/0/query?where=CODIGOESTACION+IN+%28%27292%27%2C%27293%27%2C%27294%27%2C%271045%27%29&outFields=CODIGOESTACION%2CDENOMINACION&outSR=4326&f=geojson
```

La respuesta real está en [`contracts/samples/stops.geojson`](../contracts/samples/stops.geojson).

## Resultado

- **Coincidencias:** exactamente cuatro features.
- **Tipo del código:** `CODIGOESTACION` es texto, tanto en el esquema de la capa como en el GeoJSON. El filtro acepta comillas, y es la forma que se debe usar. Sin comillas también devolvió cuatro features, pero no conviene depender de eso.
- **Campos:** cada feature trae `properties.CODIGOESTACION`, `properties.DENOMINACION` y `geometry.coordinates`.
- **Coordenadas:** vienen en orden `[longitud, latitud]`, en WGS84, y caen sobre Madrid.
- **Respuesta:** HTTP 200 con `Content-Type: application/json; charset=utf-8`, no `application/geo+json`.

## Reglas para quien consuma los datos

1. **Ordenar por el manifest.** El servicio devolvió `293, 292, 1045, 294`, así que el orden de la respuesta no sirve como secuencia.
2. **Unir por `CODIGOESTACION`.** No usar `OBJECTID`: tampoco viene en esta respuesta.
3. **No confundir los códigos.** `CODIGOESTACION` es el identificador CRTM y `CODIGOEMPRESA` el código público EMT.
4. **Comprobar que lleguen las cuatro paradas** antes de dar la descarga por buena.

## Sin comprobar

La consulta se hizo desde el portátil. La descarga desde el terminal Android, incluidos sus certificados, queda para A10.
