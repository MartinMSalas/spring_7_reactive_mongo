package com.sparta.spring_7_reactive.mongo.exceptions;

/*
 * Author: M
 * Date: 10-Apr-26
 * Project Name: mongo
 * Description: Domain exception thrown when a beer cannot be found.
 */
public class BeerNotFoundException extends RuntimeException {

    public BeerNotFoundException(String message) {
        super(message);
    }
}
