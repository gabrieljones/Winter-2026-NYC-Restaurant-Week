import org.scalatest.funsuite.AnyFunSuite

class LinkFetcherTest extends AnyFunSuite {

  test("fetchGoogleLink returns None when API key is missing") {
    // Assuming environment variables are not set during this test run or we can't easily unset them in JVM
    // but typically in this environment they won't be set unless we set them.
    // If they ARE set, we'd need to mock sys.env, which is hard in Scala/Java.
    // For now, we rely on the fact that the keys are likely missing in the CI/test environment.

    // However, to be safe, we can check if keys are present.
    if (sys.env.get("GOOGLE_MAPS_API_KEY").isEmpty) {
        val result = LinkFetcher.fetchGoogleLink("Test Restaurant", "123 Test St")
        assert(result.isEmpty)
    }
  }

  test("fetchYelpLink returns None when API key is missing") {
    if (sys.env.get("YELP_API_KEY").isEmpty) {
        val result = LinkFetcher.fetchYelpLink("Test Restaurant", "123 Test St")
        assert(result.isEmpty)
    }
  }
}
