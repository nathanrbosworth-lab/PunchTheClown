# Punch Board Frame Lock

Approved on 2026-10-02 after visual review of build `0.4.4-m4-alpha`.

## Locked visual baseline

The Punch the Clown ready screen and active Punch board must retain:

- The ornate red/orange carved carnival frame derived from the approved reference artwork.
- Asset source: `punch_ornate_frame.webp`.
- Vertical frame scale: `1.06`.
- Vertical overhang: `0.03` above and below the 900 x 900 board.
- Existing measured bulb socket positions.
- Existing multicolor chase behavior and event reactions.
- Existing 900 x 900 Punch board dimensions and touch/grid geometry.

The cream strips are part of the underlying game board. The approved taller frame visually covers more of them without changing the board itself.

## Change control

Do not alter the frame artwork, frame proportions, bulb positions, bulb animation behavior, board size, or Punch touch geometry unless the user explicitly approves a new frame revision.

The frozen M3 rollback points remain untouched.

## Illustrated artwork safe area

Illustrated clown composition is governed by `docs/PUNCH_ILLUSTRATED_ART_SPEC.md`.

The locked frame does not move or resize for individual clown artwork. Each clown is authored on a 900 x 900 canvas with its critical subject contained inside the centered 720 x 720 safe-art area.
