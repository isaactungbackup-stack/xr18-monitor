#!/usr/bin/env bash
# WING Mixer Control - Demo Status Output
# This shows what the natural text channel status output looks like

cat << 'EOF'
=== WING Channel Status ===

--- INPUT CHANNELS ---
CH01 Fader: 75.0% (-3.2 dB), Mute: OFF, Pan: C, Gain: 0.0 dB
CH02 Fader: 60.0% (-8.0 dB), Mute: OFF, Pan: L40/R40, Gain: 0.0 dB
CH03 Fader: 80.0% (-1.6 dB), Mute: ON 🔇, Pan: C, Gain: 0.0 dB
CH04 Fader: 70.0% (-5.0 dB), Mute: OFF, Pan: L20/R60, Gain: 0.0 dB
CH05 Fader: 65.0% (-6.4 dB), Mute: OFF, Pan: C, Gain: 0.0 dB
CH06 Fader: 55.0% (-10.2 dB), Mute: OFF, Pan: R, Gain: 0.0 dB
CH07 Fader: 0.0% (-inf dB), Mute: ON 🔇, Pan: C, Gain: 0.0 dB
CH08 Fader: 90.0% (+0.6 dB), Mute: OFF, Pan: L60/R20, Gain: 0.0 dB
... (channels 9-48 similar format) ...

--- BUSES ---
BUS01 Fader: 85.0% (-1.2 dB), Mute: OFF
BUS02 Fader: 70.0% (-5.0 dB), Mute: OFF
BUS03 Fader: 60.0% (-8.0 dB), Mute: OFF
BUS04 Fader: 75.0% (-3.2 dB), Mute: OFF
BUS05 Fader: 50.0% (-12.0 dB), Mute: ON 🔇
BUS06 Fader: 65.0% (-6.4 dB), Mute: OFF
BUS07 Fader: 80.0% (-1.6 dB), Mute: OFF
BUS08 Fader: 55.0% (-10.2 dB), Mute: OFF
BUS09 Fader: 0.0% (-inf dB), Mute: OFF
BUS10 Fader: 0.0% (-inf dB), Mute: OFF
BUS11 Fader: 0.0% (-inf dB), Mute: OFF
BUS12 Fader: 0.0% (-inf dB), Mute: OFF
BUS13 Fader: 0.0% (-inf dB), Mute: OFF
BUS14 Fader: 0.0% (-inf dB), Mute: OFF
BUS15 Fader: 0.0% (-inf dB), Mute: OFF
BUS16 Fader: 0.0% (-inf dB), Mute: OFF

=== Quick Scan (CH 1-8) ===
CH1[ ] 75.0% (-3.2 dB)
CH2[ ] 60.0% (-8.0 dB)
CH3[M] 80.0% (-1.6 dB)
CH4[ ] 70.0% (-5.0 dB)
CH5[ ] 65.0% (-6.4 dB)
CH6[ ] 55.0% (-10.2 dB)
CH7[M] 0.0% (-inf dB)
CH8[ ] 90.0% (+0.6 dB)

WING: 6 active, 2 muted

EOF
echo "(Demo output - V1.0001)"