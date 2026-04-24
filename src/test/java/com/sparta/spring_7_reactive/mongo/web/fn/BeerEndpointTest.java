package com.sparta.spring_7_reactive.mongo.web.fn;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import com.sparta.spring_7_reactive.mongo.mapper.BeerMapperImpl;
import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

// ---
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationContext;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/*
 * Author: M
 * Date: 10-Apr-26
 * Project Name: mongo
 * Description: Integration tests for Beer functional endpoints using WebTestClient,
 * Testcontainers, and reactive Spring Security test support.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureWebTestClient
class BeerEndpointTest {

    /**
     * Testcontainers-managed MongoDB instance automatically wired into Spring Boot
     * through @ServiceConnection.
     */
    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @Autowired
    private ApplicationContext applicationContext;


    /**
     * Spring-managed reactive test client used to exercise the real HTTP layer.
     */
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient
                .bindToApplicationContext(this.applicationContext)
                .apply(springSecurity())
                .configureClient()
                .build();
    }
    /**
     * Mocked reactive JWT decoder bean for the Spring test context.
     *
     * Why this is needed:
     * - the application is configured as an OAuth2 resource server with JWT support
     * - in WebFlux tests, Spring Security expects a ReactiveJwtDecoder bean
     * - @MockitoBean replaces the bean inside the Spring context for the test slice
     *
     * Note:
     * The actual request authentication in these tests is supplied by mockJwt().
     * This bean exists to satisfy the reactive resource server infrastructure.
     */
    //@MockitoBean
    //private ReactiveJwtDecoder jwtDecoder;

    /**
     * Given a valid beer payload,
     * when the client creates a beer as an authenticated user with csrf protection satisfied,
     * then the API should return 201 Created with a Location header and created beer body.
     */
    @Test
    @DisplayName("given valid beer payload when create beer then return created beer")
    void givenValidBeerPayload_whenCreateBeer_thenReturnCreatedBeer() {
        authenticatedClientWithCsrf().post()
                .uri(BeerRouterConfig.BEER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(getTestBeerDto())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.beerId").isNotEmpty()
                .jsonPath("$.beerName").isEqualTo("Space Dust")
                .jsonPath("$.beerStyle").isEqualTo("IPA")
                .jsonPath("$.upc").isEqualTo("123213")
                .jsonPath("$.quantityOnHand").isEqualTo(12);
    }

    /**
     * Given an invalid beer payload,
     * when the client creates a beer as an authenticated user with csrf protection satisfied,
     * then the API should return 400 Bad Request with an error body.
     */
    @Test
    @DisplayName("given invalid beer payload when create beer then return bad request")
    void givenInvalidBeerPayload_whenCreateBeer_thenReturnBadRequest() {
        BeerDTO invalidBeer = getTestBeerDto();
        invalidBeer.setBeerName("");

        authenticatedClientWithCsrf().post()
                .uri(BeerRouterConfig.BEER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBeer)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").exists();
    }

    /**
     * Given malformed JSON,
     * when the client creates a beer as an authenticated user with csrf protection satisfied,
     * then the API should return 400 Bad Request with the standard malformed JSON message.
     */
    @Test
    @DisplayName("given malformed json when create beer then return bad request")
    void givenMalformedJson_whenCreateBeer_thenReturnBadRequest() {
        String malformedJson = """
                {
                  "beerName": "Space Dust",
                  "beerStyle": "IPA",
                  "upc": "123213",
                  "price": 10.00,
                  "quantityOnHand":
                }
                """;

        authenticatedClientWithCsrf().post()
                .uri(BeerRouterConfig.BEER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Malformed JSON request body");
    }

    /**
     * Given an existing beer id,
     * when the client checks whether the beer exists as an authenticated user,
     * then the API should return 200 OK with body true.
     */
    @Test
    @DisplayName("given existing beer id when check exists then return true")
    void givenExistingBeerId_whenCheckExists_thenReturnTrue() {
        BeerDTO savedBeer = saveTestBeer();

        authenticatedClient().get()
                .uri(BeerRouterConfig.BEER_PATH_EXISTS, savedBeer.getBeerId())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(Boolean.class)
                .value(exists -> assertThat(exists).isTrue());
    }

    /**
     * Given a missing beer id,
     * when the client checks whether the beer exists as an authenticated user,
     * then the API should return 200 OK with body false.
     */
    @Test
    @DisplayName("given missing beer id when check exists then return false")
    void givenMissingBeerId_whenCheckExists_thenReturnFalse() {
        authenticatedClient().get()
                .uri(BeerRouterConfig.BEER_PATH_EXISTS, "missing-id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(Boolean.class)
                .value(exists -> assertThat(exists).isFalse());
    }

    /**
     * Given an existing beer id,
     * when the client retrieves the beer by id as an authenticated user,
     * then the API should return 200 OK with the beer body.
     */
    @Test
    @DisplayName("given existing beer id when get beer by id then return beer")
    void givenExistingBeerId_whenGetBeerById_thenReturnBeer() {
        BeerDTO savedBeer = saveTestBeer();

        authenticatedClient().get()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.beerId").isEqualTo(savedBeer.getBeerId())
                .jsonPath("$.beerName").isEqualTo(savedBeer.getBeerName())
                .jsonPath("$.beerStyle").isEqualTo(savedBeer.getBeerStyle());
    }

    /**
     * Given a missing beer id,
     * when the client retrieves the beer by id as an authenticated user,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given missing beer id when get beer by id then return not found")
    void givenMissingBeerId_whenGetBeerById_thenReturnNotFound() {
        authenticatedClient().get()
                .uri(BeerRouterConfig.BEER_PATH_ID, "missing-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given at least one beer exists,
     * when the client lists all beers as an authenticated user,
     * then the API should return a non-empty array.
     */
    @Test
    @DisplayName("given beers in database when list beers then return beer collection")
    void givenBeersInDatabase_whenListBeers_thenReturnBeerCollection() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(BeerRouterConfig.BEER_PATH)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0));
    }

    /**
     * Given beers in the database,
     * when the client filters by beer name as an authenticated user,
     * then the API should return matching beers.
     */
    @Test
    @DisplayName("given beer name filter when list beers then return matching beers")
    void givenBeerNameFilter_whenListBeers_thenReturnMatchingBeers() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path(BeerRouterConfig.BEER_PATH)
                        .queryParam("beerName", "Space Dust")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0))
                .jsonPath("$[0].beerName").isEqualTo("Space Dust");
    }

    /**
     * Given beers in the database,
     * when the client filters by beer style as an authenticated user,
     * then the API should return matching beers.
     */
    @Test
    @DisplayName("given beer style filter when list beers then return matching beers")
    void givenBeerStyleFilter_whenListBeers_thenReturnMatchingBeers() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path(BeerRouterConfig.BEER_PATH)
                        .queryParam("beerStyle", "IPA")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0))
                .jsonPath("$[0].beerStyle").isEqualTo("IPA");
    }

    /**
     * Given beers in the database,
     * when the client filters by both beer name and beer style as an authenticated user,
     * then the API should return matching beers using the combined filter branch.
     */
    @Test
    @DisplayName("given beer name and beer style filters when list beers then return matching beers")
    void givenBeerNameAndBeerStyleFilters_whenListBeers_thenReturnMatchingBeers() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path(BeerRouterConfig.BEER_PATH)
                        .queryParam("beerName", "Space Dust")
                        .queryParam("beerStyle", "IPA")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0))
                .jsonPath("$[0].beerName").isEqualTo("Space Dust")
                .jsonPath("$[0].beerStyle").isEqualTo("IPA");
    }

    /**
     * Given beers in the database,
     * when the client sends a blank beerName filter as an authenticated user,
     * then the API should ignore the blank filter and return all beers.
     */
    @Test
    @DisplayName("given blank beer name filter when list beers then ignore blank filter and return beers")
    void givenBlankBeerNameFilter_whenListBeers_thenIgnoreBlankFilterAndReturnBeers() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path(BeerRouterConfig.BEER_PATH)
                        .queryParam("beerName", " ")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0));
    }

    /**
     * Given beers in the database,
     * when the client sends a blank beerStyle filter as an authenticated user,
     * then the API should ignore the blank filter and return all beers.
     */
    @Test
    @DisplayName("given blank beer style filter when list beers then ignore blank filter and return beers")
    void givenBlankBeerStyleFilter_whenListBeers_thenIgnoreBlankFilterAndReturnBeers() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path(BeerRouterConfig.BEER_PATH)
                        .queryParam("beerStyle", " ")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0));
    }

    /**
     * Given beers in the database,
     * when the client sends a blank beerName and a valid beerStyle as an authenticated user,
     * then the API should ignore the blank beerName and apply the beerStyle filter.
     */
    @Test
    @DisplayName("given blank beer name and valid beer style when list beers then filter by beer style")
    void givenBlankBeerNameAndValidBeerStyle_whenListBeers_thenFilterByBeerStyle() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path(BeerRouterConfig.BEER_PATH)
                        .queryParam("beerName", " ")
                        .queryParam("beerStyle", "IPA")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0))
                .jsonPath("$[0].beerStyle").isEqualTo("IPA");
    }

    /**
     * Given beers in the database,
     * when the client sends a valid beerName and a blank beerStyle as an authenticated user,
     * then the API should ignore the blank beerStyle and apply the beerName filter.
     */
    @Test
    @DisplayName("given valid beer name and blank beer style when list beers then filter by beer name")
    void givenValidBeerNameAndBlankBeerStyle_whenListBeers_thenFilterByBeerName() {
        saveTestBeer();

        authenticatedClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path(BeerRouterConfig.BEER_PATH)
                        .queryParam("beerName", "Space Dust")
                        .queryParam("beerStyle", " ")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()")
                .value(Integer.class, value -> assertThat(value).isGreaterThan(0))
                .jsonPath("$[0].beerName").isEqualTo("Space Dust");
    }

    /**
     * Given a valid payload and an existing beer id,
     * when the client updates the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 200 OK with the updated beer body.
     */
    @Test
    @DisplayName("given valid payload and existing beer id when update beer then return updated beer")
    void givenValidPayloadAndExistingBeerId_whenUpdateBeer_thenReturnUpdatedBeer() {
        BeerDTO savedBeer = saveTestBeer();
        savedBeer.setBeerName("Updated Space Dust");
        savedBeer.setBeerStyle("Double IPA");

        authenticatedClientWithCsrf().put()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(savedBeer)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.beerId").isEqualTo(savedBeer.getBeerId())
                .jsonPath("$.beerName").isEqualTo("Updated Space Dust")
                .jsonPath("$.beerStyle").isEqualTo("Double IPA");
    }

    /**
     * Given an invalid payload,
     * when the client updates the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 400 Bad Request with an error body.
     */
    @Test
    @DisplayName("given invalid payload when update beer then return bad request")
    void givenInvalidPayload_whenUpdateBeer_thenReturnBadRequest() {
        BeerDTO savedBeer = saveTestBeer();
        savedBeer.setBeerStyle("");

        authenticatedClientWithCsrf().put()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(savedBeer)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").exists();
    }

    /**
     * Given a valid payload and a missing beer id,
     * when the client updates the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given valid payload and missing beer id when update beer then return not found")
    void givenValidPayloadAndMissingBeerId_whenUpdateBeer_thenReturnNotFound() {
        BeerDTO beerToUpdate = getTestBeerDto();

        authenticatedClientWithCsrf().put()
                .uri(BeerRouterConfig.BEER_PATH_ID, "missing-id")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(beerToUpdate)
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given malformed JSON,
     * when the client updates the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 400 Bad Request with the standard malformed JSON message.
     */
    @Test
    @DisplayName("given malformed json when update beer then return bad request")
    void givenMalformedJson_whenUpdateBeer_thenReturnBadRequest() {
        BeerDTO savedBeer = saveTestBeer();

        String malformedJson = """
                {
                  "beerName": "Updated Space Dust",
                  "beerStyle": "IPA",
                  "upc": "123213",
                  "price": 15.50,
                  "quantityOnHand":
                }
                """;

        authenticatedClientWithCsrf().put()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Malformed JSON request body");
    }

    /**
     * Given a partial payload and an existing beer id,
     * when the client patches the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 200 OK with the updated beer body.
     */
    @Test
    @DisplayName("given partial payload and existing beer id when patch beer then return updated beer")
    void givenPartialPayloadAndExistingBeerId_whenPatchBeer_thenReturnUpdatedBeer() {
        BeerDTO savedBeer = saveTestBeer();

        BeerDTO patchPayload = new BeerDTO();
        patchPayload.setBeerName("Patched Beer Name");

        authenticatedClientWithCsrf().patch()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patchPayload)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.beerId").isEqualTo(savedBeer.getBeerId())
                .jsonPath("$.beerName").isEqualTo("Patched Beer Name");
    }

    /**
     * Given a partial payload and a missing beer id,
     * when the client patches the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given partial payload and missing beer id when patch beer then return not found")
    void givenPartialPayloadAndMissingBeerId_whenPatchBeer_thenReturnNotFound() {
        BeerDTO patchPayload = new BeerDTO();
        patchPayload.setBeerName("Patched Beer Name");

        authenticatedClientWithCsrf().patch()
                .uri(BeerRouterConfig.BEER_PATH_ID, "missing-id")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(patchPayload)
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given malformed JSON,
     * when the client patches the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 400 Bad Request with the standard malformed JSON message.
     */
    @Test
    @DisplayName("given malformed json when patch beer then return bad request")
    void givenMalformedJson_whenPatchBeer_thenReturnBadRequest() {
        BeerDTO savedBeer = saveTestBeer();

        String malformedJson = """
                {
                  "beerName": "Patched Beer Name",
                }
                """;

        authenticatedClientWithCsrf().patch()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Malformed JSON request body");
    }

    /**
     * Given an existing beer id,
     * when the client deletes the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 200 OK with the deleted beer body.
     */
    @Test
    @DisplayName("given existing beer id when delete beer then return deleted beer")
    void givenExistingBeerId_whenDeleteBeer_thenReturnDeletedBeer() {
        BeerDTO savedBeer = saveTestBeer();

        authenticatedClientWithCsrf().delete()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.beerId").isEqualTo(savedBeer.getBeerId())
                .jsonPath("$.beerName").isEqualTo(savedBeer.getBeerName());
    }

    /**
     * Given an existing beer id,
     * when the client deletes the beer as an authenticated user with csrf protection satisfied,
     * then the resource should no longer be retrievable.
     */
    @Test
    @DisplayName("given existing beer id when delete beer then beer is no longer retrievable")
    void givenExistingBeerId_whenDeleteBeer_thenBeerIsNoLongerRetrievable() {
        BeerDTO savedBeer = saveTestBeer();

        authenticatedClientWithCsrf().delete()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .exchange()
                .expectStatus().isOk();

        authenticatedClient().get()
                .uri(BeerRouterConfig.BEER_PATH_ID, savedBeer.getBeerId())
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Given a missing beer id,
     * when the client deletes the beer as an authenticated user with csrf protection satisfied,
     * then the API should return 404 Not Found.
     */
    @Test
    @DisplayName("given missing beer id when delete beer then return not found")
    void givenMissingBeerId_whenDeleteBeer_thenReturnNotFound() {
        authenticatedClientWithCsrf().delete()
                .uri(BeerRouterConfig.BEER_PATH_ID, "missing-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Creates a beer through the secured POST endpoint using an authenticated client
     * with csrf protection satisfied and returns the created resource.
     *
     * Why this helper exists:
     * - many tests require a persisted beer first
     * - centralizing the setup keeps tests shorter and more focused
     * - the assertions here verify that the create flow is healthy before downstream tests continue
     */
    private BeerDTO saveTestBeer() {
        BeerDTO savedBeer = authenticatedClientWithCsrf().post()
                .uri(BeerRouterConfig.BEER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(getTestBeerDto())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(BeerDTO.class)
                .returnResult()
                .getResponseBody();

        if (savedBeer == null) {
            throw new IllegalStateException("Expected created beer in POST response body, but body was null");
        }

        if (savedBeer.getBeerId() == null || savedBeer.getBeerId().isBlank()) {
            throw new IllegalStateException("Expected created beer to contain a valid beerId");
        }

        return savedBeer;
    }

    /**
     * Returns a WebTestClient configured with a mocked authenticated JWT.
     *
     * Use this helper for safe requests such as GET.
     *
     * Notes:
     * - this application is secured as an OAuth2 resource server
     * - in tests, mockJwt() simulates a successfully authenticated JWT request
     * - explicit claims make the test principal easier to inspect while debugging
     */
    private WebTestClient authenticatedClient() {
        return webTestClient.mutateWith(
                mockJwt().jwt(jwt -> jwt
                        .subject("test-user")
                        .claim("scope", "beer.read beer.write")
                )
        );
    }

    /**
     * Returns a WebTestClient configured with a mocked authenticated JWT
     * plus a CSRF token.
     *
     * Use this helper for state-changing requests such as POST, PUT, PATCH, and DELETE.
     *
     * Why this helper exists:
     * - mockJwt() satisfies authentication for the resource server
     * - csrf() satisfies CSRF protection for unsafe HTTP methods
     * - a 403 on POST/PUT/PATCH/DELETE usually means authentication succeeded
     *   but CSRF protection rejected the request
     */
    private WebTestClient authenticatedClientWithCsrf() {
        return webTestClient.mutateWith(
                mockJwt().jwt(jwt -> jwt
                        .subject("test-user")
                        .claim("scope", "beer.read beer.write")
                )
        ).mutateWith(csrf());
    }

    /**
     * Returns a valid BeerDTO fixture for integration tests.
     */
    private BeerDTO getTestBeerDto() {
        return new BeerMapperImpl().beerToBeerDto(getTestBeer());
    }

    /**
     * Returns a valid Beer domain fixture for integration tests.
     */
    private Beer getTestBeer() {
        return Beer.builder()
                .beerName("Space Dust")
                .beerStyle("IPA")
                .price(BigDecimal.TEN)
                .quantityOnHand(12)
                .upc("123213")
                .build();
    }
}