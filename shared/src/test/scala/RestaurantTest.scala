import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import upickle.default._

class RestaurantTest extends AnyFunSuite with Matchers {

  test("JSON parsing handles missing fields as empty strings if converter does so") {
    val json = """[
      {
        "name": "Test",
        "pdf_url": "",
        "slug": "test",
        "url": "http://test.com",
        "venueAddress": "123 Main St",
        "latitude": 40.0,
        "longitude": -74.0,
        "summary": "Summary",
        "website": "",
        "collections": "",
        "borough": "Manhattan",
        "neighborhood": "Chelsea",
        "primaryCategory": "Food",
        "primaryLocation": "NYC",
        "restaurantInclusionWeek": "Week 1",
        "image_url": "",
        "partnerId": 123,
        "meal_type": "$30 Lunch",
        "tags": "Italian"
      }
    ]"""

    val restaurants = read[List[Restaurant]](json)
    if (restaurants.nonEmpty) {
        val r = restaurants.head
    }

    restaurants should have size 1
    restaurants.head.name shouldBe "Test"
    restaurants.head.pdf_url shouldBe ""
    restaurants.head.website shouldBe ""
  }
}
