package ru.kershov.blogapp.controllers;

import com.fasterxml.jackson.annotation.JsonView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.kershov.blogapp.services.CaptchaCodeService;
import ru.kershov.blogapp.services.RegisterUserService;
import ru.kershov.blogapp.utils.JsonViews;

@RestController
@RequestMapping("/api/auth")
public class ApiAuthController {
    @Autowired
    private RegisterUserService registerUserService;

    @Autowired
    private CaptchaCodeService captchaCodeService;

    @PostMapping(value="/register", produces = "application/json")
    public ResponseEntity<?> registerUser(@RequestParam(name="e_mail") String email,
                                          @RequestParam(name="name") String name,
                                          @RequestParam(name="password") String password,
                                          @RequestParam(name="captcha") String captcha,
                                          @RequestParam(name="captcha_secret") String captchaSecretCode) {
        return registerUserService.registerUser(email, name, password, captcha, captchaSecretCode);
    }

    @GetMapping(value="/captcha", produces = "application/json")
    @JsonView(JsonViews.Name.class)
    public ResponseEntity<?> getCaptcha() {
        return captchaCodeService.getCaptcha();
    }
}
