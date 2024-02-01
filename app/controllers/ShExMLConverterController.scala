package controllers

import com.herminiogarcia.shexml.MappingLauncher
import com.typesafe.config.Config
import play.api.Logging
import play.api.libs.json.Json

import javax.inject._
import play.api.mvc._

import java.net.URL
import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

@Singleton
class ShExMLConverterController @Inject()(val controllerComponents: ControllerComponents, config: Config) extends BaseController with Logging {

  def main() = Action { implicit request: Request[AnyContent] =>
    val shexmlVersion = new MappingLauncher().getClass.getPackage.getImplementationVersion
    Ok(Json.obj("online" -> true, "shexml-version" -> shexmlVersion))
  }

  def convert() = Action { implicit request: Request[AnyContent] =>
    val source = request.getQueryString("dataURL")
    val mappingRules = request.getQueryString("mappingURL")
    mappingRules.flatMap(m => source.map(s => {
      val rules = getMappingRulesFromURL(m)
      val finalMappingRules = rules.replaceFirst("SOURCE src <placeholder>", s"SOURCE src <$s>")
      handleAnswerWithContentNegotiation(finalMappingRules)
    })) match {
      case Some(r) => r
      case None => {
        logger.error(s"Error due to missing arguments on request with dataURL=$source and mappingURL=$mappingRules")
        BadRequest("Unrecognized combination of parameters")
      }
    }
  }

  def convertRepository(id: String) = Action { implicit request: Request[AnyContent] =>
    logger.info("Requested repository conversion of " + id)
    val rules = getMappingRulesFromFile(config.getString("ehri.shexml.converter.mapping.rules.institution"))
    val finalMappingRules = getFinalMappingRules("repository", rules, id)
    handleAnswerWithContentNegotiation(finalMappingRules)
  }

  def convertUnit(id: String) = Action { implicit request: Request[AnyContent] =>
    logger.info("Requested unit conversion of " + id)
    val rules = getMappingRulesFromFile(config.getString("ehri.shexml.converter.mapping.rules.unit"))
    val finalMappingRules = getFinalMappingRules("unit", rules, id)
    handleAnswerWithContentNegotiation(finalMappingRules)
  }

  private def handleAnswerWithContentNegotiation(mappingRules: String)(implicit request: Request[AnyContent]): Result = {
    val AcceptsRDFXML = Accepting("application/rdf+xml")
    val AcceptsNTriples = Accepting("application/n-triples")
    val AcceptsJsonLD = Accepting("application/ld+json")
    val AcceptsTurtle = Accepting("text/turtle")
    render {
      case AcceptsRDFXML() => handleMapping(mappingRules, "RDF/XML")
      case AcceptsNTriples() => handleMapping(mappingRules, "N-Triples")
      case AcceptsJsonLD() => handleMapping(mappingRules, "JSON-LD")
      case AcceptsTurtle() => handleMapping(mappingRules, "Turtle")
      case _ => handleMapping(mappingRules, request.getQueryString("format").getOrElse("RDF/XML"))
    }
  }

  private def handleMapping(mappingRules: String, format: String): Result = {
    Try(new MappingLauncher().launchMapping(mappingRules, format)) match {
      case Success(result) => {
        val contentType = {
          if(format.toLowerCase.contains("xml")) XML
          else if(format.toLowerCase.contains("json")) JSON
          else TEXT
        }
        Ok(result).as(contentType)
      }
      case Failure(exception) => {
        logger.error("Error while translating to RDF ", exception)
        InternalServerError("Something went wrong!")
      }
    }
  }

  private def getFinalMappingRules(theType: String, rules: String, id: String): String = {
    val portalURL = new URL(config.getString("ehri.shexml.converter.portal.url"))
    val apiSource = if(theType.toLowerCase == "repository")
        s"SOURCE repositories <${portalURL.getProtocol}://${portalURL.getHost}:${portalURL.getPort}/api/v1/$id>"
      else s"SOURCE holdings <${portalURL.getProtocol}://${portalURL.getHost}:${portalURL.getPort}/api/v1/$id>"
    val termsSource = s"SOURCE terms <${portalURL.getProtocol}://${portalURL.getHost}:${portalURL.getPort}/units/$id/export?asFile=true>"
    rules
      .replaceFirst("SOURCE (repositories|holdings) <placeholder>", apiSource)
      .replaceFirst("SOURCE terms <placeholder>", termsSource)
  }

  private def getMappingRulesFromFile(path: String): String = getMappingRulesWithClosing(Source.fromFile(path))

  private def getMappingRulesFromURL(url: String): String = getMappingRulesWithClosing(Source.fromURL(url))

  private def getMappingRulesWithClosing(bufferedSource: BufferedSource): String = {
    try { bufferedSource.iterator.mkString } finally { bufferedSource.close() }
  }
}
