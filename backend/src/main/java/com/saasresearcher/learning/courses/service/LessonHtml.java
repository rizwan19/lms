package com.saasresearcher.learning.courses.service;

import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/** Allowlisted lesson HTML (spec §7): text structure, links and simple tables; no scripts, frames or handlers. */
public final class LessonHtml {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "h2", "h3", "h4", "ul", "ol", "li", "strong", "em", "b", "i", "u", "s",
                    "blockquote", "code", "pre", "hr", "a", "table", "thead", "tbody", "tr", "th", "td")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href").onElements("a")
            .requireRelsOnLinks("noopener", "noreferrer", "nofollow")
            .allowAttributes("colspan", "rowspan").matching(Pattern.compile("[1-9][0-9]?")).onElements("td", "th")
            .toFactory();

    private LessonHtml() {
    }

    static String sanitize(String html) {
        return html == null ? "" : POLICY.sanitize(html);
    }
}
