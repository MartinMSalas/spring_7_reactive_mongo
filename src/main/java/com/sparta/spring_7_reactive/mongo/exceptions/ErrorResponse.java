package com.sparta.spring_7_reactive.mongo.exceptions;

/*
 * Author: M
 * Date: 09-Apr-26
 * Project Name: mongo
 * Description: beExcellent
 */
public class ErrorResponse {

    private final String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
