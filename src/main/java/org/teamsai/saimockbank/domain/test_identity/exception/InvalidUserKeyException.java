package org.teamsai.saimockbank.domain.test_identity.exception;

public class InvalidUserKeyException extends CustomException{
    public InvalidUserKeyException(ErrorCode errorCode){
        super(errorCode);
    }
}
