package com.homeway.HttpServer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
@EnableWebMvc
@Controller
public class CookieManager {
    private static Map<String, String> domainCookies = new HashMap<>(1024);

    @RequestMapping(value = "/", method = RequestMethod.GET)
    @ResponseBody
    public String getCookie(@RequestParam("domain") String domain) {
        return domainCookies.entrySet().stream()
            .filter(e -> domain.endsWith(e.getKey()))
            .map(Entry::getValue)
            .collect(Collectors.joining("; "));
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    @ResponseBody
    public String setCookie(@RequestParam("domain") String domain,
                            @RequestParam("cookies") String cookies) throws IOException {

        domainCookies.put(domain, cookies);

        return domain;
    }

    public static void main(String[] args) {
        SpringApplication.run(CookieManager.class, args);
    }
}
