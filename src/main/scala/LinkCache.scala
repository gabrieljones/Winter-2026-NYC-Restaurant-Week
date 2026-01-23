import upickle.default.{read => uread, write => uwrite, _}
import os._

object LinkCache {

  // Load the cache file. Returns empty list if file doesn't exist.
  def load(path: String): List[Restaurant] = {
    val p = os.Path(path, os.pwd)
    if (os.exists(p)) {
      try {
        uread[List[Restaurant]](os.read(p))
      } catch {
        case e: Exception =>
          println(s"Warning: Failed to parse cache at $path: ${e.getMessage}")
          List.empty
      }
    } else {
      List.empty
    }
  }

  // Save the list to the cache file, sorted canonically.
  def save(path: String, restaurants: List[Restaurant]): Unit = {
    val p = os.Path(path, os.pwd)
    // Canonical sort: Name, then Address
    val sorted = restaurants.sortBy(r => (r.name, r.venueAddress))
    val json = uwrite(sorted, indent = 2)
    os.write.over(p, json, createFolders = true)
  }

  // Merge cached links into raw restaurants.
  // Matches based on Name + Address.
  def apply(raw: List[Restaurant], cache: List[Restaurant]): List[Restaurant] = {
    val cacheMap = cache.map(r => (r.name + "|" + r.venueAddress) -> r).toMap

    raw.map { r =>
      cacheMap.get(r.name + "|" + r.venueAddress) match {
        case Some(cached) =>
          r.copy(
            resy_url = if (cached.resy_url.nonEmpty) cached.resy_url else r.resy_url,
            google_maps_url = if (cached.google_maps_url.nonEmpty) cached.google_maps_url else r.google_maps_url,
            yelp_url = if (cached.yelp_url.nonEmpty) cached.yelp_url else r.yelp_url
          )
        case None => r
      }
    }
  }
}
