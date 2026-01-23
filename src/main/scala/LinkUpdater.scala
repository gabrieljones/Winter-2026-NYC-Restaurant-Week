import os._

object LinkUpdater {
  def main(args: Array[String]): Unit = {
    val dataDir = os.pwd / "data"
    os.makeDir.all(dataDir)

    val cachePath = (dataDir / "restaurants.json").toString
    val sourceXlsx = "rw_restaurants_2026-01-19.xlsx"

    println("Reading source data...")
    val rawRestaurants = XlsxConverter.read(sourceXlsx)

    println("Loading link cache...")
    val cachedRestaurants = LinkCache.load(cachePath)

    println("Merging cache...")
    val mergedRestaurants = LinkCache.apply(rawRestaurants, cachedRestaurants)

    println("Fetching missing links...")
    val updatedRestaurants = mergedRestaurants.map { r =>
      val googleLink = if (r.google_maps_url.isEmpty) {
        LinkFetcher.fetchGoogleLink(r.name, r.venueAddress).getOrElse("")
      } else r.google_maps_url

      val yelpLink = if (r.yelp_url.isEmpty) {
        LinkFetcher.fetchYelpLink(r.name, r.venueAddress).getOrElse("")
      } else r.yelp_url

      if (googleLink != r.google_maps_url || yelpLink != r.yelp_url) {
          println(s"Updated links for ${r.name}")
      }

      r.copy(
        google_maps_url = googleLink,
        yelp_url = yelpLink
      )
    }

    println(s"Saving canonical link cache to $cachePath...")
    LinkCache.save(cachePath, updatedRestaurants)
    println("Done.")
  }
}
