# Downloads airports codes and keeps only the necessary keys
# we only care about airports with scheduled flights, no helipads sorry
# depends on csvtojson from npm

curl https://ourairports.com/data/airports.csv | \
csvtojson | \
jq '.[] | select(.scheduled_service == "yes") | {type, name, latitude_deg, longitude_deg, elevation_ft, iso_country, municipality, iata_code, ident}' > airport-codes_onlyscheduled.json
