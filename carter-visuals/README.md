# Carter Visuals (Fabric, client-side, Minecraft 1.21.4)

- Picture sky: warm sunset gradient tint at golden hour (/carter sky 0-1)
- Wet ground: sky-tinted puddles with grazing-angle (Fresnel) reflection (/carter puddles, /carter reflection)
- Ripples: expanding rings on puddles during rain (/carter ripples)
- Streak rain: extra thin streaks over vanilla rain (/carter streaks)
- /carter quality 0|1|2 (low/med/high), /carter toggle, key V toggles

Performance: no extra render passes or shaders. Puddle layout is cached and rebuilt every 2 s
(or when you move 6+ blocks). Ripples and streaks are capped per quality level.

NOTE: puddle "reflections" are sky-colour approximations, not true mirror reflections of buildings/players.
Config file: .minecraft/config/carter-visuals.json

Build: push to GitHub, run the "Build mod" Action, download the jar (not the -sources one).
