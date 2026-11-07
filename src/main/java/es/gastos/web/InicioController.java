package es.gastos.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio(Authentication auth, Model model) {
        model.addAttribute("email", auth.getName());
        return "inicio";
    }
}