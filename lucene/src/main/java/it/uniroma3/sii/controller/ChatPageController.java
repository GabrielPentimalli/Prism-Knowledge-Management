package it.uniroma3.sii.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatPageController {

    @GetMapping("/chat")
    public String legacyChatRedirect() {
        return "redirect:/home";
    }
}
