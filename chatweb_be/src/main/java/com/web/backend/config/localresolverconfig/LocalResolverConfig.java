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
    private static final String ACCEPT_LANGUAGE_STRING = "Accept-Language";
    private static final String EN_STRING = "en";
    private static final String JA_STRING = "ja";
    private static final String VI_STRING = "vi";

    @Override
    @NonNull
    public Locale resolveLocale(@NonNull HttpServletRequest request) {
        String languageHeader = request.getHeader(ACCEPT_LANGUAGE_STRING);
        if (!StringUtils.hasLength(languageHeader)) {
            return Locale.ENGLISH;
        }

        List<Locale.LanguageRange> list = Locale.LanguageRange.parse(languageHeader);
        Locale locale = Locale.lookup(list, List.of(Locale.of(EN_STRING), Locale.of(VI_STRING), Locale.of(JA_STRING)));

        return locale != null ? locale : Locale.ENGLISH;
    }
}