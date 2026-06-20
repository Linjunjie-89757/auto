package com.company.autoplatform.webuiautomation;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Component;

@Component
public class WebUiPlaywrightLocatorValidationRunner implements WebUiLocatorValidationRunner {

    private final WebUiLocatorSupport locatorSupport;

    public WebUiPlaywrightLocatorValidationRunner(WebUiLocatorSupport locatorSupport) {
        this.locatorSupport = locatorSupport;
    }

    @Override
    public LocatorValidationResult validate(LocatorValidationContext context) {
        try (Playwright playwright = Playwright.create();
             Browser browser = browserType(playwright, context.browserType())
                     .launch(new BrowserType.LaunchOptions().setHeadless(context.headless()));
             BrowserContext browserContext = browser.newContext()) {
            browserContext.setDefaultTimeout(context.timeoutMs());
            Page page = browserContext.newPage();
            page.navigate(context.baseUrl(), new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            Locator locator = locatorSupport.resolve(page, context.locatorType(), context.locatorValue());
            int count = locator.count();
            if (count > 0) {
                locator.first().scrollIntoViewIfNeeded();
            }
            return new LocatorValidationResult(
                    count > 0,
                    count,
                    null,
                    page.screenshot()
            );
        } catch (RuntimeException exception) {
            return new LocatorValidationResult(false, 0, exception.getMessage(), null);
        }
    }

    private BrowserType browserType(Playwright playwright, String browserType) {
        return switch (WebUiAutomationFormatSupport.normalizeBrowserType(browserType)) {
            case "FIREFOX" -> playwright.firefox();
            case "WEBKIT" -> playwright.webkit();
            default -> playwright.chromium();
        };
    }
}
