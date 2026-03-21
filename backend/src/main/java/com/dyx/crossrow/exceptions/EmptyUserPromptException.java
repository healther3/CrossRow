package com.dyx.crossrow.exceptions;

public class EmptyUserPromptException extends RuntimeException
{
    public EmptyUserPromptException() {
        super("用户提示词为空");
    }
}
