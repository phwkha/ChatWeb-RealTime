package com.web.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;

@Configuration
@Profile({ "dev", "test" })
public class OpenApiConfig {

    private static final String APACE_2_0_STRING = "Apace 2.0";
    private static final String API_DOCUMENT_FOR_BACKEND_STRING = "API Document for backend";
    private static final String BAD_REQUEST_D_LI_U_KH_NG_H_P_L_STRING = "Bad Request - Dữ liệu không hợp lệ";

    private static final String BEARERAUTH_STRING = "bearerAuth";
    private static final String BEARER_STRING = "bearer";

    private static final String COM_WEB_BACKEND_CONTROLLER_STRING = "com.web.backend.controller";
    private static final String FORBIDDEN_KH_NG_C_QUY_N_TRUY_C_P_STRING = "Forbidden - Không có quyền truy cập";
    private static final String HTTPS_SPRINGDOC_ORG_STRING = "https://springdoc.org";
    private static final String INTERNAL_SERVER_ERROR_L_I_H_TH_NG_STRING = "Internal Server Error - Lỗi hệ thống";
    private static final String JWT_STRING = "JWT";

    private static final String STR_400_STRING = "400";
    private static final String STR_401_STRING = "401";
    private static final String STR_403_STRING = "403";
    private static final String STR_500_STRING = "500";

    private static final String UNAUTHORIZED_CH_A_X_C_TH_C_STRING = "Unauthorized - Chưa xác thực";

    @Bean
    public GroupedOpenApi groupedOpenApi(@Value("${openapi.service.api-docs}") String apiDocs) {
        return GroupedOpenApi.builder().group(apiDocs)
                .packagesToScan(COM_WEB_BACKEND_CONTROLLER_STRING)
                .build();
    }

    @Bean
    public OpenAPI openAPI(
            @Value("${openapi.service.title}") String title,
            @Value("${openapi.service.version}") String version,
            @Value("${openapi.service.server}") String serverUrl) {
        final String securitySchemeName = BEARERAUTH_STRING;
        return new OpenAPI()
                .servers(List.of(new Server().url(serverUrl)))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        securitySchemeName,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme(BEARER_STRING)
                                                .bearerFormat(JWT_STRING)))
                .security(List.of(new SecurityRequirement().addList(securitySchemeName)))
                .info(new Info().title(title)
                        .version(version)
                        .description(API_DOCUMENT_FOR_BACKEND_STRING)
                        .license(new License().name(APACE_2_0_STRING).url(HTTPS_SPRINGDOC_ORG_STRING)));
    }

    @Bean
    public OpenApiCustomizer customGlobalOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(pathItem -> 
                    pathItem.readOperations().forEach(this::addGlobalApiResponses)
                );
            }
        };
    }

    private void addGlobalApiResponses(Operation operation) {
        ApiResponses apiResponses = operation.getResponses();
        if (apiResponses == null) {
            apiResponses = new ApiResponses();
            operation.setResponses(apiResponses);
        }
        addApiResponseIfMissing(apiResponses, STR_400_STRING, BAD_REQUEST_D_LI_U_KH_NG_H_P_L_STRING);
        addApiResponseIfMissing(apiResponses, STR_401_STRING, UNAUTHORIZED_CH_A_X_C_TH_C_STRING);
        addApiResponseIfMissing(apiResponses, STR_403_STRING, FORBIDDEN_KH_NG_C_QUY_N_TRUY_C_P_STRING);
        addApiResponseIfMissing(apiResponses, STR_500_STRING, INTERNAL_SERVER_ERROR_L_I_H_TH_NG_STRING);
    }

    private void addApiResponseIfMissing(ApiResponses apiResponses, String code, String description) {
        if (!apiResponses.containsKey(code)) {
            apiResponses.addApiResponse(code, new ApiResponse().description(description));
        }
    }
}
