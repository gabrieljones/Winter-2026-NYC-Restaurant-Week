import org.scalajs.dom
import org.scalajs.dom.document
import scalatags.JsDom.all._
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default._

object App {
  def main(args: Array[String]): Unit = {
    if (js.typeOf(dom.window) != "undefined") {
      dom.window.addEventListener("DOMContentLoaded", (_: dom.Event) => {
        setupMap()
      })
    }
  }

  def setupMap(): Unit = {
    val L = js.Dynamic.global.L

    val map = L.map("map", js.Dynamic.literal(
      "center" -> js.Array(40.7396, -73.9739),
      "crs" -> L.CRS.EPSG3857,
      "zoom" -> 12,
      "zoomControl" -> true,
      "preferCanvas" -> false
    ))

    L.tileLayer(
      "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
      js.Dynamic.literal(
        "minZoom" -> 0,
        "maxZoom" -> 19,
        "maxNativeZoom" -> 19,
        "noWrap" -> false,
        "attribution" -> "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors",
        "subdomains" -> "abc",
        "detectRetina" -> false,
        "tms" -> false,
        "opacity" -> 1
      )
    ).addTo(map)

    dom.fetch("restaurants.json").toFuture.flatMap { response =>
      if (!response.ok) throw new Exception(s"Failed to fetch data: ${response.statusText}")
      response.text().toFuture
    }.map { jsonText =>
        val restaurants = read[List[Restaurant]](jsonText)
        processRestaurants(map, restaurants, L)
    }.recover {
      case e: Exception => dom.console.error(s"Error loading map: ${e.getMessage}")
    }
  }

  def processRestaurants(map: js.Dynamic, restaurants: List[Restaurant], L: js.Dynamic): Unit = {
    val allMealTypes: List[String] = restaurants.flatMap(r => r.meal_type.split(";").map(_.trim).filter(_.nonEmpty)).toSet.toList.sorted
    val allTags: List[String] = restaurants.flatMap(r => r.tags.split(";").map(_.trim).filter(_.nonEmpty)).toSet.toList.sorted

    // Create groups for each meal type
    // map: MealType -> FeatureGroup
    val mealTypeGroups = allMealTypes.map { mt =>
      mt -> L.featureGroup(js.Dynamic.literal()).addTo(map)
    }.toMap

    restaurants.zipWithIndex.foreach { case (r, idx) =>
      val rTags = r.tags.split(";").map(_.trim).filter(_.nonEmpty).toList
      val rMealTypes = r.meal_type.split(";").map(_.trim).filter(_.nonEmpty).toList

      val marker = L.marker(
        js.Array(r.latitude, r.longitude),
        js.Dynamic.literal(
            "maxWidth" -> 350,
            "lazy" -> true,
            "tags" -> js.Array(rTags: _*)
        )
      )

      // Create Popup Content
      def encode(s: String) = js.URIUtils.encodeURIComponent(s)
      val googleLink = s"https://www.google.com/maps/search/?api=1&query=${encode(r.name + " " + r.venueAddress)}"
      val yelpLink = s"https://www.yelp.com/search?find_desc=${encode(r.name)}&find_loc=${encode(r.venueAddress)}"
      val resyLink = s"https://resy.com/cities/ny?query=${encode(r.name)}"

      val websiteUrl = if (r.website.nonEmpty) r.website else "#"

      val imagePart: Modifier = if (r.image_url.nonEmpty) img(src := r.image_url, width := "300px") else ""
      val pdfPart: Modifier = if (r.pdf_url.nonEmpty) span(a(href := r.pdf_url, target := "_blank", attr("style") := "font-size: 15px", "PDF"), br) else ""

      val popupHtml = div(
        id := s"html_popup_$idx",
        attr("style") := "width: 100%;",
        a(href := r.url, target := "_blank", attr("style") := "font-size: 20px", r.name), br,
        a(href := r.url, target := "_blank", imagePart),
        div(
            a(href := websiteUrl, target := "_blank", attr("style") := "font-size: 15px", "Website"), " | ",
            a(href := resyLink, target := "_blank", attr("style") := "font-size: 15px", "Rezy"), " | ",
            a(href := googleLink, target := "_blank", attr("style") := "font-size: 15px", "Google Maps"), " | ",
            a(href := yelpLink, target := "_blank", attr("style") := "font-size: 15px", "Yelp")
        ),
        br,
        pdfPart,
        div(
          attr("style") := "display: flex;",
          div(
            attr("style") := "padding-right: 4em",
            ul(attr("style") := "list-style: none; padding: 0;", rTags.map(t => li(t)))
          ),
          div(
            ul(attr("style") := "list-style: none; padding: 0;", rMealTypes.map(m => li(m)))
          )
        )
      ).render

      // Use render.outerHTML because bindPopup expects a String or DOM Element.
      // Scalatags .render returns a DOM Element, which Leaflet supports.
      // But passing it directly might lose events if not attached carefully, though here it is fine.
      // However, usually we pass HTML string or Element.
      // uwrite(popupHtml) was used in server implementation to produce a JS string of HTML.
      // Here we can pass the DOM element directly.

      marker.bindPopup(popupHtml, js.Dynamic.literal("maxWidth" -> "100%"))
      marker.bindTooltip(s"<div>${r.name}</div>", js.Dynamic.literal("sticky" -> true))

      // Add to groups
      rMealTypes.foreach { mt =>
        mealTypeGroups.get(mt).foreach { group =>
          group.addLayer(marker)
        }
      }
    }

    // Layer Control Logic
    val priceGroups = allMealTypes.groupBy { mt =>
      val pricePart = mt.split(" ").find(_.startsWith("$"))
      pricePart.getOrElse("Other")
    }

    val layerTreeChildren = priceGroups.toList.sortBy(_._1).map { case (price, types) =>
      val typeChildren = types.sorted.map { mt =>
        js.Dynamic.literal(
            "label" -> mt,
            "layer" -> mealTypeGroups(mt)
        )
      }

      js.Dynamic.literal(
        "label" -> s" $price",
        "selectAllCheckbox" -> true,
        "children" -> js.Array(typeChildren: _*)
      )
    }

    L.control.layers.tree(
        null,
        js.Dynamic.literal(
            "label" -> " Select All",
            "selectAllCheckbox" -> "Un/select all",
            "children" -> js.Array(layerTreeChildren: _*)
        ),
        js.Dynamic.literal(
            "collapsed" -> false,
            "closedSymbol" -> "+",
            "openedSymbol" -> "-",
            "spaceSymbol" -> "&nbsp;",
            "selectorBack" -> false,
            "namedToggle" -> false,
            "collapseAll" -> "",
            "expandAll" -> "",
            "labelIsSelector" -> "both"
        )
    ).addTo(map)

    // Tag Filter Button
    L.control.tagFilterButton(js.Dynamic.literal(
        "data" -> js.Array(allTags: _*),
        "icon" -> "fa-filter",
        "clearText" -> "clear",
        "filterOnEveryClick" -> true,
        "openPopupOnHover" -> true,
        "collapsed" -> false
    )).addTo(map)
  }
}
