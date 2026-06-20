package com.fitmatch.exception;

import com.fitmatch.common.enums.ErrorCode;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, String.format("%s not found with id: %s", resource, id));
    }
}
