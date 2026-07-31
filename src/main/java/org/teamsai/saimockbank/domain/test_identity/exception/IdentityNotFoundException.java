package org.teamsai.saimockbank.domain.test_identity.exception;

public class IdentityNotFoundException extends CustomException{
    public IdentityNotFoundException(ErrorCode errorCode){
        super(errorCode);
    }
}
