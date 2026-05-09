package com.fyp.floodmonitoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails through Resend's HTTP API. SMTP via
 * smtp.resend.com:465 silently failed on Railway (port 465 blocked), so
 * this class now goes through {@link ResendHttpClient} which posts to
 * https://api.resend.com/emails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailSenderResolver senders;
    private final ResendHttpClient    resend;

    @Value("${app.email.dev-recipient:}")
    private String devRecipient;

    @Value("${app.environment:development}")
    private String environment;

    @Async
    public void sendPasswordResetCode(String toEmail, String code) {
        String subject = "Your FloodWatch password reset code";
        String html = buildOtpEmailHtml(
                "Reset your password",
                "Use the code below to set a new password for your FloodWatch account. " +
                        "If you didn't request this, you can safely ignore this email.",
                code,
                "Reset code");
        resend.sendHtml(
                senders.headerFor(EmailSenderResolver.PASSWORD_RESET),
                resolveRecipient(toEmail),
                subject,
                html);
    }

    @Async
    public void sendBroadcastAlert(String toEmail, String title, String body) {
        resend.sendText(
                senders.headerFor(EmailSenderResolver.BROADCAST),
                resolveRecipient(toEmail),
                "[FloodWatch] " + title,
                body + "\n\n— FloodWatch Operations");
    }

    private String resolveRecipient(String originalEmail) {
        if ("development".equals(environment) && devRecipient != null && !devRecipient.isBlank()) {
            log.info("[Email DEV] Redirecting from {} → {} (dev mode)", originalEmail, devRecipient);
            return devRecipient;
        }
        return originalEmail;
    }

    private String buildOtpEmailHtml(String heading, String intro, String code, String codeLabel) {
        return String.format("""
                <!doctype html>
                <html>
                  <body style="margin:0;padding:0;background:#f4f6f8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#111827">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 16px">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(15,23,42,0.06),0 8px 24px rgba(15,23,42,0.05)">
                            <tr>
                              <td style="padding:32px 40px 8px 40px">
                                <table role="presentation" cellpadding="0" cellspacing="0">
                                  <tr>
                                    <td style="padding-right:10px;vertical-align:middle">
                                      <div style="width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,#2563eb,#1d4ed8);display:inline-block;line-height:36px;text-align:center;color:#ffffff;font-weight:700;font-size:18px">FW</div>
                                    </td>
                                    <td style="vertical-align:middle">
                                      <span style="font-size:16px;font-weight:600;letter-spacing:-0.01em;color:#0f172a">FloodWatch</span>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:8px 40px 0 40px">
                                <h1 style="margin:16px 0 8px 0;font-size:22px;line-height:1.3;font-weight:600;color:#0f172a;letter-spacing:-0.01em">%s</h1>
                                <p style="margin:0 0 24px 0;font-size:15px;line-height:1.55;color:#475569">%s</p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:0 40px">
                                <div style="border:1px solid #e2e8f0;border-radius:12px;background:#f8fafc;padding:20px;text-align:center">
                                  <div style="font-size:11px;font-weight:600;letter-spacing:0.12em;text-transform:uppercase;color:#64748b;margin-bottom:8px">%s</div>
                                  <div style="font-family:'SF Mono',Menlo,Consolas,monospace;font-size:34px;font-weight:700;letter-spacing:0.35em;color:#0f172a">%s</div>
                                  <div style="font-size:12px;color:#94a3b8;margin-top:10px">Expires in 10 minutes</div>
                                </div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:24px 40px 32px 40px">
                                <p style="margin:0;font-size:13px;line-height:1.6;color:#64748b">
                                  Never share this code with anyone. FloodWatch will never ask for it by phone or email.
                                </p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:20px 40px;background:#f8fafc;border-top:1px solid #e2e8f0">
                                <p style="margin:0;font-size:12px;line-height:1.5;color:#94a3b8;text-align:center">
                                  Sent by FloodWatch &middot; Real-time flood monitoring for vulnerable communities
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """,
                escapeHtml(heading),
                escapeHtml(intro),
                escapeHtml(codeLabel),
                escapeHtml(code));
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
