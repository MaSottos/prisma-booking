package com.prisma.prismabooking.utils;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
@Slf4j
public class ConflictException extends RuntimeException {

    public ConflictException(String attr, String value) {
        super(String.format("Resource alredy exists with %s = %s", attr, value));
        log.info("Resource alredy exists with {} = {}", attr, value);
    }
}