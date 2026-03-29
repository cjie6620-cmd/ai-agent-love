package com.yupi.yuaiagent.constant;

/**
 * 文件相关常量。
 * 统一管理路径常量，避免在多个类里硬编码同一目录。
 */
public interface FileConstant {

    /**
     * 项目临时文件的根目录。
     * 默认放在当前项目目录下的 tmp 文件夹。
     */
    String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";
}
