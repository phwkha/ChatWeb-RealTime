package com.web.backend.config.localresolverconfig;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class LocalResolverConfig extends AcceptHeaderLocaleResolver implements WebMvcConfigurer {
    @Override
    @NonNull
    public Locale resolveLocale(@NonNull HttpServletRequest request) {
        String languageHeader = request.getHeader(org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE);
        if (!StringUtils.hasLength(languageHeader)) {
            return Locale.ENGLISH;
        }

        List<Locale.LanguageRange> list = Locale.LanguageRange.parse(languageHeader);
        Locale locale = Locale.lookup(list, List.of(Locale.of("en"), Locale.of("vi"), Locale.of("ja")));

        return locale != null ? locale : Locale.ENGLISH;
    }
}