package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.common.BadRequestException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebUiPlaywrightBrowserRunner implements WebUiBrowserRunner {

    private final WebUiLocatorSupport locatorSupport;

    public WebUiPlaywrightBrowserRunner(WebUiLocatorSupport locatorSupport) {
        this.locatorSupport = locatorSupport;
    }

    @Override
    public List<StepExecutionResult> run(WebUiRunContext context) {
        List<StepExecutionResult> results = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = browserType(playwright, context.browserType())
                     .launch(new BrowserType.LaunchOptions().setHeadless(context.headless()));
             BrowserContext browserContext = browser.newContext()) {
            browserContext.setDefaultTimeout(context.defaultTimeoutMs());
            Page page = browserContext.newPage();
            for (WebUiCaseStepEntity step : context.steps()) {
                results.add(executeStep(page, context.baseUrl(), step));
                if (!results.get(results.size() - 1).success() && !Boolean.TRUE.equals(step.getContinueOnFailure())) {
                    break;
                }
            }
        }
        return results;
    }

    private StepExecutionResult executeStep(Page page, String baseUrl, WebUiCaseStepEntity step) {
        long startedAt = System.nanoTime();
        try {
            byte[] screenshotBytes = null;
            switch (step.getStepType()) {
                case "OPEN" -> page.navigate(WebUiExecutionEngineSupport.resolveOpenUrl(step.getInputValue(), baseUrl));
                case "CLICK" -> locator(page, step).click();
                case "FILL" -> locator(page, step).fill(requiredInput(step));
                case "CLEAR" -> locator(page, step).clear();
                case "HOVER" -> locator(page, step).hover();
                case "DOUBLE_CLICK" -> locator(page, step).dblclick();
                case "RIGHT_CLICK" -> locator(page, step).click(new Locator.ClickOptions().setButton(com.microsoft.playwright.options.MouseButton.RIGHT));
                case "PRESS_KEY" -> page.keyboard().press(requiredInput(step));
                case "SELECT" -> locator(page, step).selectOption(requiredInput(step));
                case "FILE_UPLOAD" -> locator(page, step).setInputFiles(java.nio.file.Path.of(requiredInput(step)));
                case "WAIT_FOR" -> locator(page, step).waitFor();
                case "ASSERT_VISIBLE" -> assertVisible(page, step);
                case "ASSERT_TEXT" -> assertText(page, step);
                case "ASSERT_URL" -> assertUrl(page, step);
                case "ASSERT_TITLE" -> assertTitle(page, step);
                case "ASSERT_ATTRIBUTE" -> assertAttribute(page, step);
                case "ASSERT_COUNT" -> assertCount(page, step);
                case "SCREENSHOT" -> screenshotBytes = page.screenshot();
                default -> throw new BadRequestException("Unsupported step type: " + step.getStepType());
            }
            return new StepExecutionResult(step, true, elapsedMs(startedAt), null, screenshotBytes);
        } catch (PlaywrightException | BadRequestException | IllegalArgumentException exception) {
            return new StepExecutionResult(step, false, elapsedMs(startedAt), exception.getMessage(), captureFailureScreenshot(page));
        }
    }

    private void assertVisible(Page page, WebUiCaseStepEntity step) {
        if (!locator(page, step).isVisible()) {
            throw new BadRequestException("Element is not visible: " + step.getLocatorValue());
        }
    }

    private void assertText(Page page, WebUiCaseStepEntity step) {
        String actual = locator(page, step).textContent();
        String expected = requiredInput(step);
        if (actual == null || !actual.contains(expected)) {
            throw new BadRequestException("Element text does not contain expected value");
        }
    }

    private void assertUrl(Page page, WebUiCaseStepEntity step) {
        String expected = requiredInput(step);
        if (!page.url().contains(expected)) {
            throw new BadRequestException("Current URL does not contain expected value");
        }
    }

    private void assertTitle(Page page, WebUiCaseStepEntity step) {
        String expected = requiredInput(step);
        if (!page.title().contains(expected)) {
            throw new BadRequestException("Current title does not contain expected value");
        }
    }

    private void assertAttribute(Page page, WebUiCaseStepEntity step) {
        WebUiExecutionEngineSupport.AttributeExpectation expectation =
                WebUiExecutionEngineSupport.parseAttributeExpectation(requiredInput(step));
        String actual = locator(page, step).getAttribute(expectation.name());
        if (actual == null || !actual.contains(expectation.expectedValue())) {
            throw new BadRequestException("Element attribute does not contain expected value");
        }
    }

    private void assertCount(Page page, WebUiCaseStepEntity step) {
        int actual = locator(page, step).count();
        if (!WebUiExecutionEngineSupport.matchesCountExpectation(actual, requiredInput(step))) {
            throw new BadRequestException("Element count does not match expected value");
        }
    }

    private Locator locator(Page page, WebUiCaseStepEntity step) {
        return locatorSupport.resolve(page, step.getLocatorType(), step.getLocatorValue());
    }

    private BrowserType browserType(Playwright playwright, String browserType) {
        return switch (WebUiAutomationFormatSupport.normalizeBrowserType(browserType)) {
            case "FIREFOX" -> playwright.firefox();
            case "WEBKIT" -> playwright.webkit();
            default -> playwright.chromium();
        };
    }

    private String requiredInput(WebUiCaseStepEntity step) {
        String inputValue = WebUiAutomationFormatSupport.blankToNull(step.getInputValue());
        if (inputValue == null) {
            throw new BadRequestException("Input value cannot be blank");
        }
        return inputValue;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private byte[] captureFailureScreenshot(Page page) {
        try {
            return page.screenshot();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
