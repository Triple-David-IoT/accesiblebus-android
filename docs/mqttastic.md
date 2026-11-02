# MQTTastic: resultados de PV-MQTT
Fecha: 2 nov 2026. Broker: Mosquitto local (emulador, adb reverse tcp:1883).

- QoS 1 publicando: sí. Visto con mosquitto_sub en terminals/hmi-demo-01/receipts.
- Retained: sí. vehicles/BUS-DEMO-01/presence queda retenido.
- Will con retain: sí. Al cerrar la app (proceso muerto, socket cerrado) el offline se publica al instante.
  Pendiente: medir el tiempo ante caída silenciosa (keep alive 15 s) en móvil físico. En emulador, el modo avión no la provoca.
- Reconecta sola: sí.
- Resuscribe tras reconectar: sí (no hace falta volver a suscribir).
- Avisa de la reconexión: sí. onReconnected se dispara al reconectar tras parar y arrancar Mosquitto
  (se comprueba con la línea RECONECTADO; no se dispara en la primera conexión).
- Versión: mqttClientBom = "0.8.0", ktor = "3.5.1", coroutines = "1.11.0". Compose BOM 2026.02.01 (no es de MQTT).

Limitaciones encontradas:
- Con protocolVersion = V3_1_1, el Will no admite payload como String (activa payloadFormatIndicator, solo MQTT 5.0). Usar payload(ByteArray).
- Tras una reconexión hay que volver a publicar "online" retenido: el Will deja offline en el broker.