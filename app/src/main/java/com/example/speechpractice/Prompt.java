package com.example.speechpractice;

import androidx.annotation.DrawableRes;

public class Prompt {
    public final String id;
    public final String word;
    public final String instruction;
    @DrawableRes public final int illustration;

    public Prompt(String id, String word, String instruction, int illustration) {
        this.id = id;
        this.word = word;
        this.instruction = instruction;
        this.illustration = illustration;
    }
}
