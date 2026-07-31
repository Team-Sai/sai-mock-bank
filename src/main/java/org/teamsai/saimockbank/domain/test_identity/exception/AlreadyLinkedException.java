package org.teamsai.saimockbank.domain.test_identity.exception;

public class AlreadyLinkedException extends CustomException{
    public AlreadyLinkedException(ErrorCode errorCode){
        super(errorCode);
    }
}
