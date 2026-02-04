package com.dyx.crossrow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "crossrow.storage")
public class StorageProperties {
     String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
}
