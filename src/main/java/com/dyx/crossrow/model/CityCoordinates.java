package com.dyx.crossrow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CityCoordinates {
    private String name;
    private Double lat;
    private Double lng;
    private String type;
}