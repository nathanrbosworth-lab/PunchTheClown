#!/usr/bin/env bash
set -euo pipefail

files=(
  grid_01_clown_car.ogg
  grid_02_freesound_squeak.ogg
  grid_03_squeak_3.ogg
  grid_04_squeak_2.ogg
  grid_05_squeak_1.ogg
  grid_06_baby_1.ogg
  grid_07_baby_2.ogg
  grid_08_tiny_1.ogg
  grid_09_baby_4.ogg
)

for name in "${files[@]}"; do
  src="app/src/main/res/raw/$name"
  test -f "$src"

  sample_rate="$(ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate -of csv=p=0 "$src")"
  test -n "$sample_rate"

  tmp="$(mktemp --suffix=.ogg)"
  ffmpeg -hide_banner -loglevel error -y -i "$src"     -filter:a "asetrate=$((sample_rate / 2)),aresample=${sample_rate},atempo=2.0"     -ac 1 -ar "$sample_rate" -c:a libvorbis -q:a 4 "$tmp"

  mv "$tmp" "$src"
done

echo "One-octave-lower Punch board sounds:"
sha256sum app/src/main/res/raw/grid_*.ogg
