#!/bin/sh

iata_codes=( $(cat airline_iata_info.json | jq -r '.[] | .iata') )

for code in ${iata_codes[@]}; do
  echo "Fetching $code"
  curl http://pics.avs.io/200/200/$code.png > logos/$code.png

  colorhex=$(convert logos/$code.png -colors 16 -depth 8 -format "%c" histogram:info: | \
  sort -rn | head -2 | tail -1 | egrep -o "#[0-9A-Z]{6}")
  echo "Logo has hex color $colorhex"

  sed "s/#5d9cec/$colorhex/g" airplane.svg > airplanes_svg/$code.svg

  echo "Converting from SVG to PNG"
  rsvg-convert -h 100 airplanes_svg/$code.svg > airplanes_png/$code.png

  mkdir airplanes_rotations/$code

  for angle in $( seq 0 12 360); do
    echo "Rotating to angle $angle"
    convert airplanes_png/$code.png -distort ScaleRotateTranslate $angle \
    +repage airplanes_rotations/$code/airplane_$angle.png
   done

done
