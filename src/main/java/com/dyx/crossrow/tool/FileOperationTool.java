package com.dyx.crossrow.tool;

import cn.hutool.core.io.FileUtil;
import com.dyx.crossrow.properties.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.stringtemplate.v4.ST;

@RequiredArgsConstructor
public class FileOperationTool {
    private final StorageProperties storageProperties;
    @Tool(description = "read content from file")
    public String readFile(@ToolParam(description = "file name") String fileName) {
        String filePath = storageProperties.getFILE_SAVE_DIR() + "/file"+ '/'+ fileName;

        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool
    public String writeFile(@ToolParam(description = "file name") String fileName,
                            @ToolParam(description = "file content")String  content) {
        String filePath = storageProperties.getFILE_SAVE_DIR() + "/file"+ '/'+ fileName;
        FileUtil.mkdir(filePath);
        try {
            FileUtil.writeUtf8String(content, filePath);
            return "File saved successfully." + filePath;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }

    }
}
