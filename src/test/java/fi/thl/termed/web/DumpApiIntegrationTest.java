package fi.thl.termed.web;

import static com.jayway.restassured.RestAssured.given;
import static fi.thl.termed.util.json.JsonElementFactory.array;
import static fi.thl.termed.util.json.JsonElementFactory.object;
import static fi.thl.termed.util.json.JsonElementFactory.primitive;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import com.google.gson.JsonObject;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.Test;

public class DumpApiIntegrationTest extends BaseApiIntegrationTest {

  @Test
  public void shouldPostAndGetTrivialDump() {
    String graphId = UUID.randomUUID().toString();
    String typeId = "Concept";
    String nodeId = UUID.randomUUID().toString();

    JsonObject graphIdObject = object("id", primitive(graphId));
    JsonObject typeIdObject = object("id", primitive(typeId), "graph", graphIdObject);

    JsonObject dump = object(
        "graphs", array(graphIdObject),
        "types", array(typeIdObject),
        "nodes", array(object("id", primitive(nodeId), "type", typeIdObject)));

    // save dump
    given()
        .auth().basic(testUsername, testPassword)
        .contentType("application/json")
        .body(dump.toString())
        .when()
        .post("/api/dump")
        .then()
        .statusCode(HttpStatus.SC_NO_CONTENT);

    // read dump (limited only to previously posted data)
    given()
        .auth().basic(testUsername, testPassword)
        .when()
        .get("/api/dump?graphId=" + graphId)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .contentType(APPLICATION_JSON_UTF8_VALUE)
        .body(sameJSONAs(dump.toString())
            .allowingExtraUnexpectedFields()
            .allowingAnyArrayOrdering());
  }

}