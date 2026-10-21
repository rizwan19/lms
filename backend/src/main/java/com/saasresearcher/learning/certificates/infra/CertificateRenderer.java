package com.saasresearcher.learning.certificates.infra;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.saasresearcher.learning.certificates.entity.CertificateSnapshot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/**
 * Renders a certificate PDF from its frozen snapshot with an embedded Noto Sans font (SIL OFL 1.1). Text the font cannot
 * draw (for example scripts that need shaping, such as Bengali) fails rendering with an explicit message instead of
 * silently replacing characters in the student's name.
 */
@Component
public class CertificateRenderer {

    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    static class UnsupportedTextException extends Exception {

        UnsupportedTextException(String message) {
            super(message);
        }
    }

    byte[] render(UUID certificateId, CertificateSnapshot snapshot, String verificationUrl, byte[] logo) throws IOException, UnsupportedTextException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font regular = font(document, "/fonts/NotoSans-Regular.ttf");
            PDType0Font bold = font(document, "/fonts/NotoSans-Bold.ttf");
            requireDrawable(regular, "institution name", snapshot.institutionName());
            requireDrawable(bold, "student name", snapshot.studentName());
            requireDrawable(bold, "course title", snapshot.courseTitle());

            PDPage page = new PDPage(PAGE);
            document.addPage(page);
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Certificate of completion");
            info.setCreator("Learning Portal");
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setLineWidth(3);
                content.addRect(24, 24, PAGE.getWidth() - 48, PAGE.getHeight() - 48);
                content.stroke();
                float y = PAGE.getHeight() - 80;
                if (logo != null) {
                    PDImageXObject image = PDImageXObject.createFromByteArray(document, logo, "logo");
                    float height = 56;
                    float width = image.getWidth() * height / image.getHeight();
                    content.drawImage(image, (PAGE.getWidth() - width) / 2, y - height, width, height);
                    y -= height + 24;
                }
                y = centered(content, regular, 20, snapshot.institutionName(), y);
                y = centered(content, bold, 34, "Certificate of Completion", y - 24);
                y = centered(content, regular, 16, "This certifies that", y - 30);
                y = centered(content, bold, 30, snapshot.studentName(), y - 20);
                y = centered(content, regular, 16, "has completed the course", y - 20);
                y = centered(content, bold, 24, snapshot.courseTitle(), y - 16);
                centered(content, regular, 13, "Edition " + snapshot.edition() + " · completed on " + DATE.format(snapshot.completedOn()), y - 18);
                text(content, regular, 9, "Certificate " + certificateId, 48, 44);
                if (verificationUrl != null) {
                    PDImageXObject qr = LosslessFactory.createFromImage(document, qr(verificationUrl));
                    content.drawImage(qr, PAGE.getWidth() - 140, 48, 92, 92);
                    text(content, regular, 8, "Verify: " + verificationUrl, 48, 32);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static PDType0Font font(PDDocument document, String resource) throws IOException {
        try (InputStream in = CertificateRenderer.class.getResourceAsStream(resource)) {
            return PDType0Font.load(document, in, true);
        }
    }

    private static void requireDrawable(PDType0Font font, String field, String text) throws UnsupportedTextException {
        try {
            font.encode(text);
        } catch (IllegalArgumentException | IOException e) {
            throw new UnsupportedTextException("The " + field + " contains characters the certificate font cannot display ("
                    + "Latin, Greek and Cyrillic scripts are supported). Ask an administrator about a supported spelling.");
        }
    }

    /** Centers one line, shrinking long text (e.g. a 200-character course title) to fit inside the border. */
    private static float centered(PDPageContentStream content, PDType0Font font, float size, String text, float y) throws IOException {
        float maxWidth = PAGE.getWidth() - 120;
        float fitted = Math.max(8, Math.min(size, maxWidth * 1000 / font.getStringWidth(text)));
        float width = font.getStringWidth(text) / 1000 * fitted;
        text(content, font, fitted, text, (PAGE.getWidth() - width) / 2, y - size);
        return y - size;
    }

    private static void text(PDPageContentStream content, PDType0Font font, float size, String text, float x, float y) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static BufferedImage qr(String url) throws IOException {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300, Map.of(EncodeHintType.MARGIN, 1));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return image;
        } catch (WriterException e) {
            throw new IOException("Could not encode the verification QR code", e);
        }
    }
}
