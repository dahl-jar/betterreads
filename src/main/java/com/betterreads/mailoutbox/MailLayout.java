package com.betterreads.mailoutbox;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.web.util.HtmlUtils;

final class MailLayout {

    private static final Pattern SLOT = Pattern.compile("\\{\\{(\\w+)}}");

    private static final String SERIF = "'Libre Baskerville', Georgia, serif";

    private static final String SANS =
        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";

    private static final Map<String, String> FONTS = Map.of("serif", SERIF, "sans", SANS);

    private static final String HEADING = "heading";

    private static final String PARAGRAPH_BREAK = "\n\n";

    private static final String PAGE_TEMPLATE = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>{{heading}}</title>
        </head>
        <body style="margin:0;padding:0;background-color:#f6f4f4">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
          style="background-color:#f6f4f4">
        <tr><td align="center" style="padding:32px 16px">
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
          style="width:100%;max-width:600px;background-color:#ffffff;border:1px solid #e8e4e4">
        <tr><td style="padding:36px 32px">
        {{card}}
        </td></tr>
        </table>
        <p style="margin:20px 0 0;font-family:{{sans}};font-size:12px;line-height:16px;color:#736d6d;
          text-align:center">BetterReads &middot; betterreadsapp.com</p>
        </td></tr>
        </table>
        </body>
        </html>
        """;

    private static final String CARD_TEMPLATE = """
        {{wordmark}}
        <h1 style="margin:28px 0 0;font-family:{{serif}};font-size:24px;line-height:32px;font-weight:normal;
          color:#1c1c1c">{{heading}}</h1>
        {{paragraphs}}
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin-top:24px"><tr>
        <td bgcolor="#1c1c1c" style="background-color:#1c1c1c">
        <a href="{{link}}" style="display:inline-block;padding:12px 20px;font-family:{{sans}};font-size:14px;
          line-height:20px;font-weight:600;color:#ffffff;text-decoration:none">{{button}}</a>
        </td>
        </tr></table>
        <p style="margin:24px 0 0;font-family:{{sans}};font-size:14px;line-height:20px;color:#736d6d">
        Button not working? Open this link:</p>
        <p style="margin:4px 0 0;font-family:{{sans}};font-size:14px;line-height:20px;word-break:break-all">
        <a href="{{link}}" style="color:#3c1517;text-decoration:underline">{{link}}</a></p>
        <p style="margin:28px 0 0;padding-top:20px;border-top:1px solid #e8e4e4;font-family:{{sans}};
          font-size:14px;line-height:20px;color:#736d6d">{{footer}}</p>
        """;

    private static final String WORDMARK_TEMPLATE = """
        <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
        <td width="28" height="28" align="center" valign="middle" bgcolor="#3c1517"
          style="width:28px;height:28px;background-color:#3c1517;border-radius:4px;color:#ffffff;
          font-family:{{serif}};font-size:20px;line-height:28px;font-weight:bold">b</td>
        <td valign="middle" style="padding-left:1px;font-family:{{serif}};font-size:20px;line-height:28px;
          color:#1c1c1c">etter<b>Reads</b></td>
        </tr></table>
        """;

    private static final String WORDMARK = fill(WORDMARK_TEMPLATE, Map.of());

    private static final String PARAGRAPH_TEMPLATE = """
        <p style="margin:12px 0 0;font-family:{{sans}};font-size:16px;line-height:24px;color:#5a5555">{{text}}</p>
        """;

    private MailLayout() {
    }

    static String text(final MailContent content) {
        return String.join(PARAGRAPH_BREAK, content.paragraphs()) + PARAGRAPH_BREAK
            + content.button() + ": " + content.link() + PARAGRAPH_BREAK
            + content.footer() + "\n";
    }

    static String html(final MailContent content) {
        final String heading = HtmlUtils.htmlEscape(content.heading());
        return fill(PAGE_TEMPLATE, Map.of(HEADING, heading, "card", card(content, heading)));
    }

    private static String card(final MailContent content, final String heading) {
        final String paragraphs = content.paragraphs().stream()
            .map(MailLayout::paragraph)
            .collect(Collectors.joining());
        return fill(CARD_TEMPLATE, Map.of(
            "wordmark", WORDMARK,
            HEADING, heading,
            "paragraphs", paragraphs,
            "button", HtmlUtils.htmlEscape(content.button()),
            "link", HtmlUtils.htmlEscape(content.link()),
            "footer", HtmlUtils.htmlEscape(content.footer())));
    }

    private static String paragraph(final String text) {
        return fill(PARAGRAPH_TEMPLATE, Map.of("text", HtmlUtils.htmlEscape(text)));
    }

    private static String fill(final String template, final Map<String, String> values) {
        return SLOT.matcher(template).replaceAll(slot -> Matcher.quoteReplacement(slotValue(slot.group(1), values)));
    }

    private static String slotValue(final String slot, final Map<String, String> values) {
        final String value = values.containsKey(slot) ? values.get(slot) : FONTS.get(slot);
        return Objects.requireNonNull(value, slot);
    }

    record MailContent(String heading, List<String> paragraphs, String button, String link, String footer) {

        MailContent {
            paragraphs = List.copyOf(paragraphs);
        }
    }
}
