package com.sparta.spring_7_reactive.mongo.web.fn;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/*
 * Author: M
 * Date: 03-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
@Configuration
@RequiredArgsConstructor
public class BeerRouterConfig {

    public static final String BEER_PATH = "/api/beer";
    public static final String BEER_PATH_ID = BEER_PATH + "/{id}";

    private final BeerHandler beerHandler;
    @Bean
    public RouterFunction<ServerResponse> beerRoutes(){
        return route()
                .GET(BEER_PATH, accept(APPLICATION_JSON), beerHandler::listBeers)
                .build();
    }
}
