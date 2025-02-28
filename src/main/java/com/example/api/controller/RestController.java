package com.example.api.controller;
import com.example.api.entities.Courses;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;

@org.springframework.web.bind.annotation.RestController
public class RestController {

    @GetMapping("/home")
    public String home(){
        return "Homepage";

    }

    //get the courses
    public List<Courses> getCourses(){
        return null;
    }
}
