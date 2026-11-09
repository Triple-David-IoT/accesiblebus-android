# MQTTastic: resultados de PV-MQTT (B01)

Fechas: 2 y 6 nov 2026.

## Entorno
- Dispositivo: emulador Pixel 6a, Android API 37.2 (según Android Studio), build de depuración.
- Cliente: BusMqttClient con MQTTastic (mqttClientBom 0.8.0, ktor 3.5.1, coroutines 1.11.0) y un arnés local de pruebas.
- Broker A (laboratorio): Mosquitto 2.1.2 del compose (eclipse-mosquitto:2.1.2-alpine), puerto 1883.
- Broker B (equivalente al del curso): Mosquitto 2.0.11, paquete apt de Ubuntu 22.04, en un contenedor (puerto 1884). La máquina virtual del curso (VirtualBox x86) no se puede ejecutar en un Mac ARM. El material del curso admite usar un Ubuntu propio con apt-get install mosquitto.
- El emulador alcanza ambos brokers con adb reverse.

## Configuración (cómo se fija cada parámetro)
- clientId: MqttClient("hmi-demo-01") { ... }
- cleanSession: parámetro cleanStart (true por defecto). Comprobado en el log de ambos brokers: c1.
- keep alive: keepAliveSeconds = 15 (por defecto 60). Comprobado: k15. El broker recibe un PINGREQ cada 11-12 s (75 % del intervalo).
- Protocolo: protocolVersion = MqttProtocolVersion.V3_1_1. El log lo muestra como p4 en Mosquitto 2.1.2 y como p2 en 2.0.11 (cambia la numeración, no el protocolo). Control: mosquitto_sub -V mqttv5 aparece como p5 en 2.1.2.
- QoS: por llamada (QoS.AT_LEAST_ONCE). Retain: parámetro retain al publicar.
- Will: will { topic, payload(bytes), qos, retain } dentro del MqttClient(...) { }. En el log de 2.0.11: "Will message specified (65 bytes) (r1, q1)".
- cleanStart: fijado a true de forma explícita. (B08)

## Resultados
| Prueba | Broker A (2.1.2) | Broker B (2.0.11) | Evidencia |
| Acuse con QoS 1 | OK | OK | 01, 10 |
| Presencia retenida (suscriptor tardío recibe online al instante) | OK | OK (r1) | 02, 10 |
| Will al matar la app (force-stop) | OK, < 1 s (16:39:23) | OK, < 1 s (17:12:39) | 03, 11 |
| Reconexión automática | OK, ~1 s tras volver el broker | OK. Reintentos con espera creciente (1, 2 y 4 s); la app vuelve ~2 s después de que arranque el broker | 04, 12, 13 |
| Resuscripción tras reconectar | OK | OK | 05, 14 |
| Línea de conexión | (p4, c1, k15) | (p2, c1, k15) | 06, 15 |

Notas:
- El Will se entrega en vivo con retain=0 y a un suscriptor nuevo con retain=1.
- Al detener el broker, los suscriptores aún conectados reciben el offline de la HMI (observado). La HMI republica online al reconectar.
- El texto del log cambia entre versiones: "disconnected: connection closed by client" (2.1.2) frente a "closed its connection" (2.0.11).

## Caída silenciosa (keep alive 15 s, broker A)
Procedimiento: se congela el proceso de la app (kill -STOP con run-as) para que deje de enviar PINGREQ sin cerrar el socket. El tiempo se toma con date antes del comando y se compara con el offline en T2 y el "exceeded timeout" en el log.

| Congelación | offline | Tiempo |
| 16:33:15 | 16:33:33 | 18 s |
| 16:41:06 | 16:41:18 | 12 s |
| 16:41:31 | 16:41:47 | 16 s |

Media 15,3 s (mín. 12 s, máx. 18 s), con 1-2 s de incertidumbre por muestra. El broker desconecta si no recibe paquetes en 1,5 veces el keep alive (22,5 s) desde el último paquete recibido, y el cliente envía PINGREQ pasado el 75 % del intervalo; por eso pasan entre ~11 s y 22,5 s. Al reanudar (kill -CONT) la app reconecta y republica online. Evidencias: 07, 08.
Medida con móvil físico por Wi-Fi: no realizada.

## Limitaciones encontradas
- Con V3_1_1 el Will no admite payload como String (activa payloadFormatIndicator, solo MQTT 5.0). Usar bytes.
- Tras una reconexión hay que volver a publicar "online" retenido: el Will deja offline en el broker.
- Un cierre limpio (disconnect) descarta el Will; para que el dashboard vea offline al salir de forma limpia hay que publicarlo explícitamente (B12).

## Pendiente
- Repetir con la máquina virtual real del curso (requiere un equipo x86).
- Medida de caída silenciosa con un móvil físico.