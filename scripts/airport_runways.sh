#!/bin/bash

# This script downloads the latest airport list and their runways
# keeping only airports with scheduled service, and interesting variables
# no helipads for now ^_^'
# depends on: jq, csvtojson

echo "Creating airport json"
curl https://ourairports.com/data/airports.csv | \
 csvtojson | \
 jq '.[] | select(.scheduled_service == "yes" and .type != "heliport" and .iata_code != "")' | \
 jq -s . \
 > airport-codes_onlyscheduled.json

echo "Creating airport edn"
cat airport-codes_onlyscheduled.json | \
jet --from json --to edn --keywordize \
> ../resources/airport-codes_onlyscheduled.edn
echo "done!"

airports_tokeep=$(cat airport-codes_onlyscheduled.json | jq -r '.[] | .ident' | tr '\n' ' ')

echo "Creating runways json"
curl https://ourairports.com/data/runways.csv | \
  csvtojson | \
  jq --arg idents "${airports_tokeep}" '.[] | select(.airport_ident == ($idents | split(" ")[]))' | \
  jq -s . | \
  jet --from json --to edn --keywordize \
  > ../resources/runways_onlyscheduled.edn

echo "done!"
echo "All done! Bye!"
