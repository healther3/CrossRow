package com.dyx.crossrow.model;

public enum AgentState {
    /**
     *  空闲
     */
    IDLE,
    /**
     * 运行中
     */
    RUNNING,
    /**
     * 结束
     */
    FINISHED,
    /**
     * 等待用户输入（askHuman 调用后）
     */
    WAITING_FOR_INPUT,
    /**
     * 错误
     */
    ERROR

}
