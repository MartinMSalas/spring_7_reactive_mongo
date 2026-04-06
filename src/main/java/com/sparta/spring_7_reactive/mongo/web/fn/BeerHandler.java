package com.sparta.spring_7_reactive.mongo.web.fn;


import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import com.sparta.spring_7_reactive.mongo.service.BeerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/*
 * Author: M
 * Date: 03-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
@Component
@RequiredArgsConstructor
public class BeerHandler {
    private final BeerService beerService;

    public Mono<ServerResponse> getBeerById(ServerRequest request){
        return ServerResponse
                .ok()
                .body(beerService.findBeerById(request.pathVariable("beerId")), BeerDTO.class);
    }

    public Mono<ServerResponse> listBeers(ServerRequest request){
        return ServerResponse.ok()
                .body(beerService.findAllBeers(), BeerDTO.class);
    }
}
