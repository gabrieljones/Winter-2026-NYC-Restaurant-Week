import upickle.default._
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LinkFetcher {

  private val googleApiKey = sys.env.get("GOOGLE_MAPS_API_KEY")
  private val yelpApiKey = sys.env.get("YELP_API_KEY")

  def fetchGoogleLink(name: String, address: String): Option[String] = {
    googleApiKey.flatMap { key =>
      try {
        val query = URLEncoder.encode(s"$name $address", StandardCharsets.UTF_8.toString)
        val url = s"https://maps.googleapis.com/maps/api/place/textsearch/json?query=$query&key=$key"
        val response = requests.get(url)
        if (response.statusCode == 200) {
          val json = ujson.read(response.text())
          val results = json("results").arr
          if (results.nonEmpty) {
            val placeId = results.head("place_id").str
            Some(s"https://www.google.com/maps/place/?q=place_id:$placeId")
          } else {
            None
          }
        } else {
          println(s"Google API Error: ${response.statusCode} - ${response.text()}")
          None
        }
      } catch {
        case e: Exception =>
          println(s"Error fetching Google link for $name: ${e.getMessage}")
          None
      }
    }
  }

  def fetchYelpLink(name: String, address: String): Option[String] = {
    yelpApiKey.flatMap { key =>
      try {
        // Basic match by name and location
        // Yelp Fusion API: https://api.yelp.com/v3/businesses/search
        val url = "https://api.yelp.com/v3/businesses/search"
        val response = requests.get(
          url,
          headers = Map("Authorization" -> s"Bearer $key"),
          params = Map(
            "term" -> name,
            "location" -> address,
            "limit" -> "1"
          )
        )

        if (response.statusCode == 200) {
          val json = ujson.read(response.text())
          val businesses = json("businesses").arr
          if (businesses.nonEmpty) {
            Some(businesses.head("url").str)
          } else {
            None
          }
        } else {
          println(s"Yelp API Error: ${response.statusCode} - ${response.text()}")
          None
        }
      } catch {
        case e: Exception =>
          println(s"Error fetching Yelp link for $name: ${e.getMessage}")
          None
      }
    }
  }
}
