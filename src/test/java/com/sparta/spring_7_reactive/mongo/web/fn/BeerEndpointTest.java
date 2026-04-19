package com.sparta.spring_7_reactive.mongo.web.fn;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import com.sparta.spring_7_reactive.mongo.mapper.BeerMapperImpl;
import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Author: M
 * Date: 10-Apr-26
 * Project Name: mongo
 * Description: Integration tests for Beer functional endpoints using WebTestClient, Testcontainers, and Spring Security test support.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureWebTestClient
@ExtendWith(MockitoExtension.class)
class BeerEndpointTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        // SecurityContextHolder.clearContext();
    }
    @Autowired
    private MockMvc mockMvc;
    @Test
    void testWithMockJwt() throws Exception {
        mockMvc.perform(get("/api/secure-endpoint")
                        .with(jwt() // Mocks the JWT authentication
                                .jwt(builder -> builder.subject("test-user")) // Customize claims
                                .authorities(new SimpleGrantedAuthority("SCOPE_read")))) // Customize roles/scopes
                .andExpect(status().isOk());
    }

    /**
     * Given a valid beer payload,
     * when the client creates a beer as an authenticated user with csrf,
     * then the API should return 201 Created with Location header and created beer body.
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
                .expectHeader().exists("Location")
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
     * when the client creates a beer as an authenticated user with csrf,
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
     * when the client creates a beer as an authenticated user with csrf,
     * then the API should return 400 Bad Request with a standard malformed JSON message.
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
     * when the client updates the beer as an authenticated user with csrf,
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
     * when the client updates the beer as an authenticated user with csrf,
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
     * when the client updates the beer as an authenticated user with csrf,
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
     * when the client updates the beer as an authenticated user with csrf,
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
     * when the client patches the beer as an authenticated user with csrf,
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
     * when the client patches the beer as an authenticated user with csrf,
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
     * when the client patches the beer as an authenticated user with csrf,
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
     * when the client deletes the beer as an authenticated user with csrf,
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
     * when the client deletes the beer as an authenticated user with csrf,
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
     * when the client deletes the beer as an authenticated user with csrf,
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
     * Creates a beer through the public POST endpoint using an authenticated client with csrf
     * and returns the created resource.
     */
    private BeerDTO saveTestBeer() {
        BeerDTO savedBeer = authenticatedClientWithCsrf().post()
                .uri(BeerRouterConfig.BEER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(getTestBeerDto())
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
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
     * Use this for safe requests such as GET.
     */
    private WebTestClient authenticatedClient() {
        return webTestClient.mutateWith(mockJwt());
    }

    /**
     * Returns a WebTestClient configured with a mocked authenticated JWT and csrf token.
     * Use this for state-changing requests such as POST, PUT, PATCH, and DELETE.
     */
    private WebTestClient authenticatedClientWithCsrf() {
        return webTestClient.mutateWith(mockJwt()).mutateWith(csrf());
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

