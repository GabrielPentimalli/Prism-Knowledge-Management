package it.uniroma3.sii.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class OnboardingTemplateTest {

    public record SettingsValues(String dataRoot, String llmModel, String llmBaseUrl) {
        public String getDataRoot() { return dataRoot; }
        public String getLlmModel() { return llmModel; }
        public String getLlmBaseUrl() { return llmBaseUrl; }
    }

    @Test
    void rendersConfiguredDefaultsInsteadOfHardcodedModel() throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        assertRenderedValues(new SettingsValues(
                Path.of(properties.getProperty("prism.storage.default-data-root")).toAbsolutePath().normalize().toString(),
                properties.getProperty("llm.model"), properties.getProperty("llm.base-url")));
    }

    @Test
    void rendersCurrentSettingsIncludingCustomModelAndEndpoint() {
        assertRenderedValues(new SettingsValues("/tmp/Archivio & note", "custom:7b", "http://[::1]:12345/v1"));
    }

    private void assertRenderedValues(SettingsValues settings) {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        MockServletContext servlet = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(
                new MockHttpServletRequest(servlet, "GET", "/onboarding"), new MockHttpServletResponse());
        var context = new WebContext(exchange, Locale.ITALIAN, Map.of("settings", settings));

        var page = Jsoup.parse(engine.process("onboarding", context));

        assertThat(page.selectFirst("#dataRoot").val()).isEqualTo(settings.dataRoot());
        assertThat(page.selectFirst("#llmModel").val()).isEqualTo(settings.llmModel());
        assertThat(page.selectFirst("#llmBaseUrl").val()).isEqualTo(settings.llmBaseUrl());
    }
}
