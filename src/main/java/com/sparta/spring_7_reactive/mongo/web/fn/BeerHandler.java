package com.sparta.spring_7_reactive.mongo.web.fn;

import com.sparta.spring_7_reactive.mongo.exceptions.BeerNotFoundException;
import com.sparta.spring_7_reactive.mongo.exceptions.BeerValidationException;
import com.sparta.spring_7_reactive.mongo.exceptions.ErrorResponse;
import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import com.sparta.spring_7_reactive.mongo.service.BeerService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * Author: M
 * Date: 10-Apr-26
 * Project Name: mongo
 * Description: Functional handler for Beer endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeerHandler {

    private static final String MALFORMED_JSON_MESSAGE = "Malformed JSON request body";

    private final BeerService beerService;
    private final Validator validator;

    /**
     * POST /api/v3/beer
     * Creates a new beer after validating the incoming payload.
     */
    public Mono<ServerResponse> createBeer(ServerRequest request) {
        return request.bodyToMono(BeerDTO.class)
                .map(this::validate)
                .flatMap(validBeerDto -> beerService.saveBeer(Mono.just(validBeerDto)))
                .flatMap(savedBeer -> {
                    URI location = UriComponentsBuilder
                            .fromPath(BeerRouterConfig.BEER_PATH_ID)
                            .buildAndExpand(savedBeer.getBeerId())
                            .toUri();

                    return ServerResponse.created(location)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(savedBeer);
                })
                .onErrorResume(BeerValidationException.class, this::badRequest)
                .onErrorResume(ServerWebInputException.class, this::badRequestMalformedJson)
                .onErrorResume(DecodingException.class, this::badRequestMalformedJson);
    }

    /**
     * GET /api/v3/beer/exists/{beerId}
     *
     * Returns true if a beer exists for the given id, otherwise false.
     */
    public Mono<ServerResponse> existsById(ServerRequest request) {
        String beerId = request.pathVariable("beerId");

        if (!StringUtils.hasText(beerId)) {
            return badRequest(new IllegalArgumentException("Beer id must not be blank"));
        }

        log.info("Checking existence of beer with id={}", beerId);

        return beerService.existsBeerById(beerId)
                .flatMap(exists -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(exists));
    }

    /**
     * GET /api/v3/beer/{beerId}
     * Returns a single beer by id.
     */
    public Mono<ServerResponse> getBeerById(ServerRequest request) {
        String beerId = request.pathVariable("beerId");

        return beerService.findBeerById(beerId)
                .flatMap(beerDTO -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(beerDTO))
                .onErrorResume(BeerNotFoundException.class, this::notFound);
    }

    /**
     * GET /api/v3/beer
     * Optional query params:
     * - beerName
     * - beerStyle
     *
     * Supported combinations:
     * - no filters -> returns all beers
     * - beerName only -> filters by beer name
     * - beerStyle only -> filters by beer style
     * - both beerName and beerStyle -> filters by both values
     */
    public Mono<ServerResponse> listBeers(ServerRequest request) {
        Optional<String> beerName = request.queryParam("beerName")
                .filter(StringUtils::hasText);

        Optional<String> beerStyle = request.queryParam("beerStyle")
                .filter(StringUtils::hasText);

        log.info("Listing beers with filters beerName={} beerStyle={}",
                beerName.orElse(null),
                beerStyle.orElse(null));

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resolveBeerListing(beerName, beerStyle), BeerDTO.class);
    }

    /**
     * PUT /api/v3/beer/{beerId}
     * Replaces an existing beer after validating the full payload.
     */
    public Mono<ServerResponse> updateBeer(ServerRequest request) {
        String beerId = request.pathVariable("beerId");

        return request.bodyToMono(BeerDTO.class)
                .map(this::validate)
                .flatMap(validBeerDto -> beerService.updateBeerById(beerId, Mono.just(validBeerDto)))
                .flatMap(updatedBeer -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updatedBeer))
                .onErrorResume(BeerNotFoundException.class, this::notFound)
                .onErrorResume(BeerValidationException.class, this::badRequest)
                .onErrorResume(ServerWebInputException.class, this::badRequestMalformedJson)
                .onErrorResume(DecodingException.class, this::badRequestMalformedJson);
    }

    /**
     * PATCH /api/v3/beer/{beerId}
     * Partially updates an existing beer.
     * Full DTO validation is intentionally skipped because partial payloads are allowed.
     */
    public Mono<ServerResponse> patchBeer(ServerRequest request) {
        String beerId = request.pathVariable("beerId");

        return request.bodyToMono(BeerDTO.class)
                .flatMap(patchBeerDto -> beerService.partialUpdateBeerById(beerId, Mono.just(patchBeerDto)))
                .flatMap(updatedBeer -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updatedBeer))
                .onErrorResume(BeerNotFoundException.class, this::notFound)
                .onErrorResume(BeerValidationException.class, this::badRequest)
                .onErrorResume(ServerWebInputException.class, this::badRequestMalformedJson)
                .onErrorResume(DecodingException.class, this::badRequestMalformedJson);
    }

    /**
     * DELETE /api/v3/beer/{beerId}
     * Deletes an existing beer and returns the deleted resource.
     */
    public Mono<ServerResponse> deleteBeer(ServerRequest request) {
        String beerId = request.pathVariable("beerId");

        return beerService.deleteBeerById(beerId)
                .flatMap(deletedBeer -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(deletedBeer))
                .onErrorResume(BeerNotFoundException.class, this::notFound);
    }

    /**
     * Resolves the beer listing strategy based on the presence of optional filters.
     */
    private Flux<BeerDTO> resolveBeerListing(Optional<String> beerName, Optional<String> beerStyle) {
        if (beerName.isPresent() && beerStyle.isPresent()) {
            return beerService.findAllBeersByBeerNameAndBeerStyle(
                    beerName.get(),
                    beerStyle.get()
            );
        }

        if (beerName.isPresent()) {
            return beerService.findAllBeersByBeerName(beerName.get());
        }

        if (beerStyle.isPresent()) {
            return beerService.findAllBeersByBeerStyle(beerStyle.get());
        }

        return beerService.findAllBeers();
    }

    /**
     * Validates a full BeerDTO payload.
     * Used for POST and PUT, where a complete valid resource is expected.
     */
    private BeerDTO validate(BeerDTO beerDTO) {
        Set<ConstraintViolation<BeerDTO>> violations = validator.validate(beerDTO);

        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining(", "));

            throw new BeerValidationException(errorMessage);
        }

        return beerDTO;
    }

    /**
     * Converts a domain not-found exception into a JSON 404 response.
     */
    private Mono<ServerResponse> notFound(BeerNotFoundException ex) {
        return ServerResponse.status(404)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Converts a domain validation exception into a JSON 400 response.
     */
    private Mono<ServerResponse> badRequest(BeerValidationException ex) {
        return badRequest(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Converts an invalid input argument into a JSON 400 response.
     */
    private Mono<ServerResponse> badRequest(IllegalArgumentException ex) {
        return badRequest(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Converts malformed JSON/input parsing failures into a JSON 400 response.
     */
    private Mono<ServerResponse> badRequestMalformedJson(Throwable ex) {
        return badRequest(new ErrorResponse(MALFORMED_JSON_MESSAGE));
    }

    /**
     * Creates a standard JSON 400 response body.
     */
    private Mono<ServerResponse> badRequest(ErrorResponse errorResponse) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponse);
    }
}