package com.example.speechpractice;

import java.util.Arrays;
import java.util.List;

public final class PromptCatalog {
    private PromptCatalog() { }

    public static List<Prompt> all() {
        return Arrays.asList(
                new Prompt("apple", "apple", "What is this picture? Say the word apple.", R.drawable.illustration_apple),
                new Prompt("cup", "cup", "What is this picture? Say the word cup.", R.drawable.illustration_cup),
                new Prompt("book", "book", "Name the familiar object shown here.", R.drawable.illustration_book),
                new Prompt("chair", "chair", "Name the familiar object shown here.", R.drawable.illustration_chair),
                new Prompt("key", "key", "Name the familiar object shown here.", R.drawable.illustration_key),
                new Prompt("banana", "banana", "Name the familiar object shown here.", R.drawable.illustration_banana),
                new Prompt("ball", "ball", "Name the familiar object shown here.", R.drawable.illustration_ball),
                new Prompt("spoon", "spoon", "Name the familiar object shown here.", R.drawable.illustration_spoon),
                new Prompt("flower", "flower", "Name the familiar object shown here.", R.drawable.illustration_flower),
                new Prompt("bicycle", "bicycle", "Name the familiar object shown here.", R.drawable.illustration_bicycle)
        );
    }
}
