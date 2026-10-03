# Punch Illustrated Clown Artwork Specification

Approved on 2026-10-03 for all illustrated artwork used by the primary **Punch the Clown** mode.

## Mandatory export geometry

Every illustrated Punch clown asset must be exported on a **900 x 900 pixel canvas**.

The central subject-safe area is **720 x 720 pixels**, centered inside that canvas.

Exact coordinates:

- Canvas: `0,0` through `900,900`
- Safe-art left: `x = 90`
- Safe-art top: `y = 90`
- Safe-art right: `x = 810`
- Safe-art bottom: `y = 810`
- Safe-art size: `720 x 720`
- Margin on every side: `90 px`

## Composition rule

The clown and all visually important parts of the clown must remain inside the 720 x 720 safe-art area. This includes:

- head and hair
- hats and headwear
- hands and arms
- shoes and feet
- props held by the clown
- state/theme identifiers that are necessary to recognize the design

The background may extend across the full 900 x 900 canvas. Decorative or nonessential background material may occupy the 90 px outer margins.

Do not place critical subject detail in the outer 90 px margins because the locked ornate frame occupies that region visually.

## App behavior

The Android app treats each illustrated clown asset as a complete 900 x 900 board image.

It must:

1. Require the source bitmap to be exactly 900 x 900 pixels.
2. Draw the complete 900 x 900 asset to the complete Punch board.
3. Never rescale an already compliant 900 x 900 asset into a second 720 x 720 box.
4. Keep the centered 720 x 720 safe-art coordinates available in code for development and future tooling.
5. Leave the Punch 3 x 3 input grid and board dimensions unchanged.

The 720 x 720 rule is therefore an **art-authoring safe zone**, not a second runtime scaling operation.

## Frame relationship

The approved Punch marquee frame is separately locked in `docs/PUNCH_FRAME_LOCK.md`.

The frame remains unchanged:

- 900 x 900 Punch board
- ornate red/orange carved frame
- vertical scale `1.06`
- vertical overhang `0.03`
- approved bulb positions
- approved lighting behavior

The art must adapt to the frame. The frame must not be altered to accommodate individual clown images.

## File naming

Current illustrated Punch assets use:

`clown_illustrated_01.webp` through `clown_illustrated_10.webp`.

Future illustrated assets must follow the same 900 x 900 / 720 x 720 safe-art rule.

## Existing artwork

The current illustrated clown set predates this specification and is scheduled to be redone. Replacement artwork must satisfy this specification before it is considered approved.
