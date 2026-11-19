# A15: Tracking screen

Run on 17 Nov 2026 with two emulators against the laboratory VM (API and broker of current
`develop`, fresh stack). The passenger used one emulator and the driver used the HMI (B09–B11)
on the other, so every receipt and decision below came from the real HMI, not from curl or the
database.

The screen shows four separate facts, as the proposal asks (p. 2 and 5): registered, received
by the bus terminal, the driver's decision, and the closure. Each one has a mark (✓ or …) and
words, not only a colour. Texts follow "Mensajes que no engañan" (p. 17).

| Screenshot | State | What it shows |
| --- | --- | --- |
| `01-pending.png` | PENDING | Registered; not received yet (HMI closed); no decision; open. "Cancelar solicitud" |
| `02-received.png` | PENDING, received | The HMI opened and sent its receipt. "El terminal la ha recibido. Esto no significa que el conductor la haya leído." The decision is still pending |
| `hmi-01-received.png` | HMI | The same request on the driver's screen, with Accept and Reject |
| `03-accepted.png` | ACCEPTED | The driver tapped Accept: "El conductor ha confirmado tu solicitud.", live over MQTT |
| `04-cancel-dialog.png` | ACCEPTED | Cancel asks first: "¿Cancelar la solicitud?" with No and Sí, cancelar. No was chosen |
| `05-completed.png` | COMPLETED | The driver tapped Complete: "El conductor ha marcado la asistencia como realizada." |
| `06-rejected.png` | REJECTED | The driver rejected with "Unsafe stop": "Tu solicitud no se ha podido atender." and "Motivo: Parada no segura." |
| `07-cancelled.png` | CANCELLED | The passenger cancelled: "No hubo decisión del conductor." and "Cancelaste la solicitud." |
| `08-stop-passed.png` | EXPIRED, STOP_PASSED | The driver tapped Stop passed (B05) before deciding: "La parada ha quedado atrás sin confirmación; solicitud cerrada." |
| `09-trip-ended.png` | EXPIRED, TRIP_ENDED | Accepted, then the driver tapped End trip: notice "El viaje de demostración ha finalizado; consulta el estado.", the decision stays as confirmed, no Cancel |
| `10-font-200-top.png`, `11-font-200-bottom.png` | Font at 200 % | The screen scrolls and every button stays reachable |

## Notes

- Every change reached the passenger live, without tapping "Actualizar".
- The receipt changes no version, so a decision is never read from it: in `02-received.png`
  the terminal received the request and the decision is still pending.
- An expired or cancelled request shows "El conductor ha confirmado tu solicitud." only when it
  had been accepted before (version 3 or more), as in `09-trip-ended.png`.
- `TrackingFactsTest` checks the texts for every state, including the four messages of p. 17,
  and that a receipt is never shown as a decision.
- Seen on the HMI, outside A15: after the HMI sends its receipt for a request that arrives
  live, its own card keeps "Not yet confirmed by terminal" until Refresh. The API had recorded
  the receipt and the passenger saw it at once.
