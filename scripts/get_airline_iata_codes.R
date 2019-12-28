# Script to scrape wikipedia and get the IATA codes for all airlines possible

library(dplyr)

webpage_url <- "https://en.wikipedia.org/wiki/List_of_airline_codes"

airline_codes_table <- xml2::read_html(webpage_url) %>% 
  rvest::html_node(".wikitable") %>% 
  rvest::html_table(fill = TRUE) %>% 
  janitor::clean_names() %>% 
  filter(iata != "" & airline != "" & comments != "defunct")

airline_codes_table %>% 
  jsonlite::write_json("airline_iata_info.json")
