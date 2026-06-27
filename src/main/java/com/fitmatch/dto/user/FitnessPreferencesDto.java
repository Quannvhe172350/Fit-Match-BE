package com.fitmatch.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FitnessPreferencesDto {

    private List<String> styles;
    private String frequency;
    private String equipmentAccess;
    private String injuries;
}
