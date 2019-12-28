library(xml2)
library(tidyverse)

webpage_url <- "https://www.airfarewatchdog.com/airline-codes/"

webpage <- xml2::read_html(webpage_url)

airline_table_codes <- rvest::html_nodes(webpage, "div.airline_codes-table") %>%
  as_list()
   
has_h3 <- function(list) {
  n <- names(list)
  trimmed <- str_squish(n)
  elements_to_keep <- trimmed == "h3"
  filtered <- n[elements_to_keep]
  
  any(filtered == "h3")
}

get_variable <- function(list_element) {
  if (length(list_element) < 7 || has_h3(list_element)) {
    return(
      tibble(
        airline_name = NA_character_,
        iata_code = NA_character_
      )
    )
  }
  
  tibble(
    airline_name = list_element$div$a[[1]],
    iata_code = list_element[[4]][[1]]
  )
  
}

airline_codes <- airline_table_codes[[1]] %>% 
  map_df(get_variable) %>% 
  filter(!is.na(airline_name))
  
airline_codes %>% 
  jsonlite::write_json("airline_iata_codes.json")
