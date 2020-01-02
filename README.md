<h1 align="center">
  <a href="https://github.com/jcpsantiago/controltower">
    <img alt="control tower slack bot" src="./resources/header.png" width="100%">
  </a>
</h1>

[![CircleCI](https://circleci.com/gh/jcpsantiago/controltower/tree/master.svg?style=svg)](https://circleci.com/gh/jcpsantiago/controltower/tree/master)
[![codecov](https://codecov.io/gh/jcpsantiago/controltower/branch/master/graph/badge.svg)](https://codecov.io/gh/jcpsantiago/controltower)

We saw flights landing in nearby Tegel airport from our office, and wondered where all those airplanes came from. So I made this, and now we just ask Slack for answers. Also learned some Clojure.

![screenshot](./resources/screenshot.png)

## Features

The control tower bot supports any airport with scheduled flights in the world.
To look for flights use the command `/spot` followed by either the IATA code of the airport, the name of the city in english or _random_ to look at a random airport e.g. `/spot TXL` or `/spot Berlin`.
If the control tower doesn't see any flights in the air, you get back the current situation.

On Slack the bot needs permissions for slash commands, and incoming hooks. At work we have a dedicated channel `#planespotting` for planespotting where each sighting is posted, allowing everyone on comment on it.

* Running the spotting commands on the designated channel will post a response to that channel, visible to members
* Running the commands anywhere will post privately and is visible only to you -- this way you don't need to change channels

## Disclaimer
Airport data from [OurAirports](https://ourairports.com/)

Airline logos used to create aircraft with correct colors from [Travel Payouts](https://support.travelpayouts.com/hc/en-us/articles/203956073-Airline-logos)

Icons made by <a href="https://www.flaticon.com/authors/freepik" title="Freepik">Freepik</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a>

Maps from [MapBox](https://www.mapbox.com/) and [OpenStreetMap](https://www.openstreetmap.org)

# License

Eclipse Public License 2.0
