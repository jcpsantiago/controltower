#!/bin/sh

for angle in $( seq 0 12 360); do
  echo "This is angle $angle"
  convert airplane.png -distort ScaleRotateTranslate $angle \
  -resize 100x100^ +repage airplane_$angle.png
done
