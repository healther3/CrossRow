package com.dyx.crossrow.repository;

import com.dyx.crossrow.model.CityCoordinates;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;

@Repository
public class CityRepository {

    private static final Logger log = LoggerFactory.getLogger(CityRepository.class);
    private List<CityCoordinates> cityList;
    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // 读取 src/main/resources/data/major_cities.json
            ClassPathResource resource = new ClassPathResource("data/cities.json");

            // 解析 JSON 为 List<CityCoordinates>
            cityList = mapper.readValue(resource.getInputStream(), new TypeReference<List<CityCoordinates>>() {});

            log.info("成功加载全球主要城市数据，共 {} 个城市", cityList.size());
        } catch (IOException e) {
            log.error("加载城市数据失败", e);
        }
    }

    public List<CityCoordinates> findCities() {
        return cityList;
    }
}
