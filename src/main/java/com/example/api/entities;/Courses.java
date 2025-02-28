package com.example.api.entities;

public class Courses {
    private long id;
    private String title;
    private String description;

    public Courses(long id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }
}
