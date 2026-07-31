package org.teamsai.saimockbank.domain.test_identity.exception;

public class CustomException extends RuntimeException{
    protected ErrorCode errorCode;
    public CustomException(ErrorCode errorCode){
        this.errorCode=errorCode;
    }
    public CustomException(ErrorCode errorCode, String message){
        super(message);
        this.errorCode=errorCode;
    }
}
