package com.carbonscope.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class HelloController {
	
	@GetMapping("/")
	public String statusCheck() {
		return "carbon space backend is UP and Running!!";
	}
	
}
