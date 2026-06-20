package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.common.BadRequestException;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class WebUiLocatorSupport {

    Locator resolve(Page page, String locatorType, String locatorValue) {
        String normalizedLocatorType = normalizeLocatorType(locatorType);
        String normalizedLocatorValue = WebUiAutomationFormatSupport.blankToNull(locatorValue);
        if (normalizedLocatorValue == null) {
            throw new BadRequestException("Locator value cannot be blank");
        }
        return switch (normalizedLocatorType) {
            case "CSS" -> page.locator(normalizedLocatorValue);
            case "TEXT" -> page.getByText(normalizedLocatorValue);
            case "ROLE" -> roleLocator(page, normalizedLocatorValue);
            case "LABEL" -> page.getByLabel(normalizedLocatorValue);
            case "PLACEHOLDER" -> page.getByPlaceholder(normalizedLocatorValue);
            case "TEST_ID" -> page.getByTestId(normalizedLocatorValue);
            case "XPATH" -> page.locator("xpath=" + normalizedLocatorValue);
            default -> throw new BadRequestException("Unsupported locator type: " + normalizedLocatorType);
        };
    }

    public RoleLocator parseRoleLocator(String value) {
        String normalized = WebUiAutomationFormatSupport.blankToNull(value);
        if (normalized == null) {
            throw new BadRequestException("ROLE locator must use role:name format");
        }
        int separatorIndex = normalized.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1) {
            throw new BadRequestException("ROLE locator must use role:name format");
        }
        String role = WebUiAutomationFormatSupport.blankToNull(normalized.substring(0, separatorIndex));
        String name = WebUiAutomationFormatSupport.blankToNull(normalized.substring(separatorIndex + 1));
        if (role == null || name == null) {
            throw new BadRequestException("ROLE locator must use role:name format");
        }
        return new RoleLocator(role, name);
    }

    public record RoleLocator(String role, String name) {
    }

    private Locator roleLocator(Page page, String locatorValue) {
        RoleLocator roleLocator = parseRoleLocator(locatorValue);
        AriaRole role = AriaRole.valueOf(roleLocator.role().replace("-", "").replace("_", "").toUpperCase(Locale.ROOT));
        return page.getByRole(role, new Page.GetByRoleOptions().setName(roleLocator.name()));
    }

    private String normalizeLocatorType(String locatorType) {
        String normalized = WebUiAutomationFormatSupport.blankToNull(locatorType);
        return normalized == null ? "CSS" : normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
