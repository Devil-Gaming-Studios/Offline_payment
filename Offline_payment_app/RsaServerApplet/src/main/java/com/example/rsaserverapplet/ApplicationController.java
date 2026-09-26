package com.example.rsaserverapplet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationController {
    @GetMapping("getSecretMessage")
    public String getMessage(@RequestParam String m)
    {
        String message = m;

        EncryptionManager manager = new EncryptionManager();
        manager.initFromStrings();

        try{
            return manager.encrypt(message);
        }
        catch(Exception e)
        {}

        return null;
    }
}
