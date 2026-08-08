package com.ringwatch.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EmailNotifierTest {

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private EmailNotifier emailNotifier;

    @Test
    void successfulSendDeliversTheEmailToEveryConfiguredRecipient() throws Exception {
        emailNotifier.send("Test Subject", "Test body");

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(received.getSubject()).isEqualTo("Test Subject");
        assertThat(GreenMailUtil.getBody(received)).contains("Test body");
        assertThat(received.getAllRecipients()).hasSize(2);
    }

    @Test
    void embeddedCrlfInTheSubjectIsStrippedRatherThanInjectingAnAdditionalHeader() throws Exception {
        emailNotifier.send("Alert tx-1\r\nBcc: attacker@evil.com\r\nX-Injected: yes", "body");

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(received.getSubject()).doesNotContain("\r").doesNotContain("\n");
        assertThat(received.getAllRecipients()).hasSize(2);
    }

    @Test
    void permanentSmtpFailureFallsBackWithoutThrowing() {
        greenMail.stop();

        assertThatCode(() -> emailNotifier.send("Subject", "Body")).doesNotThrowAnyException();
    }

    @Test
    void transientSmtpFailureSucceedsOnceTheServerComesBackUp() throws Exception {
        greenMail.stop();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            greenMail.start();
        });

        emailNotifier.send("Recovered Subject", "Recovered body");

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()[0].getSubject()).isEqualTo("Recovered Subject");
    }
}
