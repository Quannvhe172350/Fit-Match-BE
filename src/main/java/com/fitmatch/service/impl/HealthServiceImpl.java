package com.fitmatch.service.impl;

import com.fitmatch.service.HealthService;
import org.springframework.stereotype.Service;

@Service
public class HealthServiceImpl implements HealthService {

    @Override
    public String getStatus() {
        return "UP";
    }
}
