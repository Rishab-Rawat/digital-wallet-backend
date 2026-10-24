package com.example.wallet.service;
public class WalletException extends RuntimeException {
    private final String code; private final int httpStatus;
    public WalletException(String code, String message, int httpStatus) { super(message); this.code=code; this.httpStatus=httpStatus; }
    public String code(){return code;} public int httpStatus(){return httpStatus;}
}
