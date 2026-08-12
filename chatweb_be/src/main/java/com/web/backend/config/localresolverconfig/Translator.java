package com.web.backend.config.localresolverconfig;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class Translator {

    private static ResourceBundleMessageSource messageSource;

    private Translator() {

    }

    @Autowired
    public void init(ResourceBundleMessageSource messageSource) {
        setStaticMessageSource(messageSource);
    }

    private static void setStaticMessageSource(ResourceBundleMessageSource source) {
        Translator.messageSource = source;
    }

    public static String tolocale(@NonNull String msgCode, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(msgCode, args, locale);
    }
}