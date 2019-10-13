package core.issues

import com.twilio.guardrail.generators.syntax.Scala.companionForStaticDefns
import com.twilio.guardrail.generators.AkkaHttp
import com.twilio.guardrail.{Client, Clients, Context}
import org.scalatest.{FunSuite, Matchers}
import support.SwaggerSpecRunner

import scala.meta._

class Issue313 extends FunSuite with Matchers with SwaggerSpecRunner {
  val swagger: String = s"""
                           |swagger: '2.0'
                           |info:
                           |  title: JupyterHub
                           |consumes:
                           |  - application/json
                           |paths:
                           |  /users/{name}:
                           |    patch:
                           |      operationId: changeUser
                           |      parameters:
                           |        - name: name
                           |          description: username
                           |          in: path
                           |          required: true
                           |          type: string
                           |        - name: data
                           |          in: body
                           |          required: true
                           |          description: Updated user info. At least one key to be updated (name or admin) is required.
                           |          schema:
                           |            type: object
                           |            properties:
                           |              name:
                           |                type: string
                           |                description: the new name (optional, if another key is updated i.e. admin)
                           |              admin:
                           |                type: boolean
                           |                description: update admin (optional, if another key is updated i.e. name)
                           |              foo:
                           |                type: object
                           |      responses:
                           |        '200':
                           |          description: The updated user info
                           |""".stripMargin

  test("Test in body generation") {
    val (
      _,
      Clients(Client(tags, className, imports, staticDefns, cls, _) :: _, Nil),
      _
      )       = runSwaggerSpec(swagger)(Context.empty, AkkaHttp)
    val cmp = companionForStaticDefns(staticDefns)

    println(cls)

    val client = q"""
      class UsersClient(host: String = "http://localhost:1234")(implicit httpClient: HttpRequest => Future[HttpResponse], ec: ExecutionContext, mat: Materializer) {
        val basePath: String = ""
        private[this] def makeRequest[T: ToEntityMarshaller](method: HttpMethod, uri: Uri, headers: scala.collection.immutable.Seq[HttpHeader], entity: T, protocol: HttpProtocol): EitherT[Future, Either[Throwable, HttpResponse], HttpRequest] = {
          EitherT(Marshal(entity).to[RequestEntity].map[Either[Either[Throwable, HttpResponse], HttpRequest]] {
            entity => Right(HttpRequest(method = method, uri = uri, headers = headers, entity = entity, protocol = protocol))
          }.recover({
            case t =>
              Left(Left(t))
          }))
        }
        def getUser(id: String, optionalIterable: Option[Iterable[String]] = None, requiredIterable: Iterable[String], headers: List[HttpHeader] = Nil): EitherT[Future, Either[Throwable, HttpResponse], GetUserResponse] = {
          val allHeaders = headers ++ scala.collection.immutable.Seq[Option[HttpHeader]]().flatten
          makeRequest(HttpMethods.GET, host + basePath + "/user/" + Formatter.addPath(id), allHeaders, FormData(List(optionalIterable.toList.flatMap {
            x => x.toList.map(x => ("optionalIterable", Formatter.show(x)))
          }, requiredIterable.toList.map(x => ("requiredIterable", Formatter.show(x)))).flatten: _*), HttpProtocols.`HTTP/1.1`).flatMap(req => EitherT(httpClient(req).flatMap(resp => resp.status match {
            case StatusCodes.OK =>
              resp.discardEntityBytes().future.map(_ => Right(GetUserResponse.OK))
            case _ =>
              FastFuture.successful(Left(Right(resp)))
          }).recover({
            case e: Throwable =>
              Left(Left(e))
          })))
        }
      }
    """

    val companion = q"""
      object UsersClient {
        def apply(host: String = "http://localhost:1234")(implicit httpClient: HttpRequest => Future[HttpResponse], ec: ExecutionContext, mat: Materializer): UsersClient = new UsersClient(host = host)(httpClient = httpClient, ec = ec, mat = mat)
        def httpClient(httpClient: HttpRequest => Future[HttpResponse], host: String = "http://localhost:1234")(implicit ec: ExecutionContext, mat: Materializer): UsersClient = new UsersClient(host = host)(httpClient = httpClient, ec = ec, mat = mat)
      }
    """

    //cls.head.right.get.structure shouldBe client.structure
    //cmp.structure shouldBe companion.structure
  }
}
