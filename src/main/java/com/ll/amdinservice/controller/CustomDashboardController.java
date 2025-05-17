package com.ll.amdinservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/custom-dashboard")
public class CustomDashboardController {

  @GetMapping
  public String customDashboard() {
    return "custom-dashboard";
  }
}