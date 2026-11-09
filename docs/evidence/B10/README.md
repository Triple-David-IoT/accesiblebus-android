# B10 — HMI actions (TM07)

Android debug build against the real API (`-PuseSampleApi=false`), local Docker Compose stack, Driver profile.
Requests created with the passenger token through REST.

| Step | Result | Image |
|---|---|---|
| Three pending requests (visits 1, 2, 3) | Cards with Accept and Reject | 01-pending-actions.png |
| Rotate, Refresh and force-stop + reopen | Exactly three `HmiAlert` lines (one per request), no repeats | 09-alert-log.png |
| Accept visit 2 | Moves to "In Progress"; `request.accepted` | 02-in-progress.png |
| Reject visit 3 | Dialog with three reasons | 03-reject-reasons.png |
| Choose "Ramp unavailable" | `request.rejected` with `closeReason` `RAMP_UNAVAILABLE` | 04-reject-event.png |
| Stop passed | Pending request of visit 1 `EXPIRED` / `STOP_PASSED`; the accepted one stays | 05-stop-passed.png |
| Complete | Request `COMPLETED`, leaves "In Progress" | 06-complete.png |
| End trip | Confirmation dialog listing the open assistances | 07-end-trip-dialog.png |
| Confirm | `trip.ended`; both buttons disabled | 08-trip-ended.png |