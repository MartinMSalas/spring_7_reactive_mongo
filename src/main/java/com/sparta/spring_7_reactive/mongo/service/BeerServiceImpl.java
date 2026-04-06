package com.sparta.spring_7_reactive.mongo.service;

import com.sparta.spring_7_reactive.mongo.domain.Beer;
import com.sparta.spring_7_reactive.mongo.mapper.BeerMapper;
import com.sparta.spring_7_reactive.mongo.model.BeerDTO;
import com.sparta.spring_7_reactive.mongo.repository.BeerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/*
 * Author: M
 * Date: 01-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class BeerServiceImpl implements BeerService {

    private final BeerRepository beerRepository;
    private final BeerMapper beerMapper;

    @Override
    public Mono<BeerDTO> saveBeer(Mono<BeerDTO> beerDtoMono) {
        log.debug("BeerService:saveBeer");

        return beerDtoMono
                .doOnNext(incomingDto -> log.debug("BeerService:saveBeer incomingDto={}", incomingDto))
                .map(beerMapper::beerDtoToBeer)
                .map(this::prepareForCreate)
                .flatMap(beerRepository::save)
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Mono<BeerDTO> findBeerById(String beerId) {
        log.debug("BeerService:findBeerById beerId={}", beerId);

        return beerRepository.findById(beerId)
                .switchIfEmpty(beerNotFound(beerId))
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Flux<BeerDTO> findAllBeers() {
        log.debug("BeerService:findAllBeers");

        return beerRepository.findAll()
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Flux<BeerDTO> findAllBeersByBeerName(String beerName) {
        log.debug("BeerService:findAllBeersByBeerName beerName={}", beerName);

        return beerRepository.findAllByBeerName(beerName)
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Flux<BeerDTO> findAllBeersByBeerStyle(String beerStyle) {
        if(beerStyle == null || beerStyle.isBlank())
            return Flux.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                    "beerStyle must not be null or blank"));

        log.debug("BeerService:findAllBeersByBeerStyle beerStyle={}", beerStyle);

        return beerRepository.findAllByBeerStyleIgnoreCase(beerStyle)
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Flux<BeerDTO> findAllBeersByBeerStyle() {
        log.debug("BeerService:findAllBeersByBeerStyle null beerStyle");

        return beerRepository.findAll()
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Mono<BeerDTO> updateBeerById(String beerId, Mono<BeerDTO> beerDtoMono) {
        log.debug("BeerService:updateBeerById beerId={}", beerId);

        return beerRepository.findById(beerId)
                .switchIfEmpty(beerNotFound(beerId))
                .flatMap(existingBeer ->
                        beerDtoMono
                                .doOnNext(incomingDto -> log.debug("BeerService:updateBeerById incomingDto={}", incomingDto))
                                .map(incomingDto -> applyCompleteUpdate(existingBeer, incomingDto))
                                .flatMap(beerRepository::save)
                )
                .map(beerMapper::beerToBeerDto);
    }
    @Override
    public Mono<BeerDTO> partialUpdateBeerById(String beerId, Mono<BeerDTO> beerDtoMono) {
        log.debug("BeerService:partialUpdateBeerById beerId={}", beerId);

        return beerRepository.findById(beerId)
                .switchIfEmpty(beerNotFound(beerId))
                .flatMap(existingBeer ->
                        beerDtoMono
                                .doOnNext(incomingDto ->
                                        log.debug("BeerService:partialUpdateBeerById incomingDto={}", incomingDto))
                                .map(incomingDto -> applyPartialUpdate(existingBeer, incomingDto))
                                .flatMap(beerRepository::save)
                )
                .map(beerMapper::beerToBeerDto);
    }

    @Override
    public Mono<BeerDTO> deleteBeerById(String beerId) {
        log.debug("BeerService:deleteBeerById beerId={}", beerId);

        return beerRepository.findById(beerId)
                .switchIfEmpty(beerNotFound(beerId))
                .flatMap(beerFound ->
                        beerRepository.delete(beerFound)
                                .thenReturn(beerMapper.beerToBeerDto(beerFound))
                );
    }

    @Override
    public Mono<Boolean> existsBeerById(String beerId) {
        log.debug("BeerService:existsBeerById beerId={}", beerId);

        return beerRepository.existsById(beerId);
    }

    private Mono<Beer> beerNotFound(String beerId) {
        return Mono.error(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Beer not found with id: " + beerId
                )
        );
    }

    private Beer prepareForCreate(Beer beer) {
        beer.setBeerId(null);
        beer.setCreatedDate(null);
        beer.setLastModifiedDate(null);
        return beer;
    }

    private Beer applyCompleteUpdate(Beer existingBeer, BeerDTO incomingDto) {
        existingBeer.setBeerName(incomingDto.getBeerName());
        existingBeer.setBeerStyle(incomingDto.getBeerStyle());
        existingBeer.setUpc(incomingDto.getUpc());
        existingBeer.setQuantityOnHand(incomingDto.getQuantityOnHand());
        existingBeer.setPrice(incomingDto.getPrice());
        return existingBeer;
    }

    private Beer applyPartialUpdate(Beer existingBeer, BeerDTO incomingDto) {
        if (incomingDto.getBeerName() != null) {
            existingBeer.setBeerName(incomingDto.getBeerName());
        }
        if (incomingDto.getBeerStyle() != null) {
            existingBeer.setBeerStyle(incomingDto.getBeerStyle());
        }
        if (incomingDto.getPrice() != null) {
            existingBeer.setPrice(incomingDto.getPrice());
        }
        if (incomingDto.getUpc() != null) {
            existingBeer.setUpc(incomingDto.getUpc());
        }
        if (incomingDto.getQuantityOnHand() != null) {
            existingBeer.setQuantityOnHand(incomingDto.getQuantityOnHand());
        }

        return existingBeer;
    }

}