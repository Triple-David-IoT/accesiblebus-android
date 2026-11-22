# Physical ramp in the lab dashboard

Run on 22 Nov 2026 in the laboratory stack, with the real ramp node (`ramp-esp32-01`, node
`ramp-phys-01`). The panel sits in the "B04 Ramps" group, under the two simulated ramps, in the
"Lab ramp test" tab of Node-RED.

## What it does

- Shows the node (`online`/`offline`, by its Will), the position from its limit switches and what
  the actuator is doing, the driver's HMI presence and the last result the node reported.
- In lab test mode, Deploy, Stow and Stop publish on `commands/vehicles/BUS-DEMO-01/ramp`, the
  topic the contract gives to the HMI or the dashboard. The mode works only while the driver's HMI
  is offline, and turns off when it comes online.
- Same rules as the HMI ramp card: no maneuver while moving, with no data for 15 s or with both
  limits ON. Stop is always enabled in test mode. The rules are checked again on every click.
- `commandId` is `lab-test-` plus the time in milliseconds, so the node's log tells where a
  command came from.

## Results

| Test | Panel | Image |
| --- | --- | --- |
| Test mode on | `online · STOWED`, Deploy and Stop enabled | 01 |
| Deploy | `online · INTERMEDIATE · DEPLOYING`, last result `STARTED`, only Stop enabled | 02 |
| Stop during the maneuver | `online · INTERMEDIATE · IDLE`, last result `STOPPED` | 03 |
| Stow | `online · STOWED · IDLE`, last result `STARTED` | 04 |
| Driver HMI online | Test mode off and every button disabled | 05 |

The positions and results are the node's own answers: the dashboard only shows them.

## Not covered

- The presence lock is not a lock on the node: if the HMI connects in the middle of a test, both
  could send commands for a few seconds. It is a laboratory mode, used with the ramp in view.
- The broker does not restrict who publishes on the command topic.
- The physical samples are not part of the B03 projection.
