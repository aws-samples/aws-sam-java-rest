package com.amazonaws.model.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonAutoDetect
@Getter
@AllArgsConstructor
public class ErrorMessage {
    private final String message;
    private final int statusCode;
}
