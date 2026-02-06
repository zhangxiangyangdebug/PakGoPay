package com.pakgopay.data.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TurnstileVerifyResponse {
    private boolean success;

    @JsonProperty("error-codes")
    private List<String> errorCodes;

    @JsonProperty("challenge_ts")
    private String challengeTs;

    private String hostname;
    private String action;
    private String cdata;
}
