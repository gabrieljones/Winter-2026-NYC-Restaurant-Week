import upickle.default.{ReadWriter, macroRW}

case class Restaurant(
    name: String,
    pdf_url: String,
    slug: String,
    url: String,
    venueAddress: String,
    latitude: Double,
    longitude: Double,
    summary: String,
    website: String,
    collections: String,
    borough: String,
    neighborhood: String,
    primaryCategory: String,
    primaryLocation: String,
    restaurantInclusionWeek: String,
    image_url: String,
    partnerId: ujson.Value,
    meal_type: String,
    tags: String
) derives ReadWriter
