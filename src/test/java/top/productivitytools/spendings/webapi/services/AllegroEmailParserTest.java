package top.productivitytools.spendings.webapi.services;

import org.junit.jupiter.api.Test;
import top.productivitytools.spendings.webapi.dto.ParsedAllegroPurchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllegroEmailParserTest {

    private final AllegroEmailParser parser = new AllegroEmailParser();

    @Test
    void shouldParseAllegroEmailHtml() {
        String html = """
                <html>
                <body>
                  <div data-cy="payment.buyerTotalValue">149,97 zł</div>
                  <table data-cy="offers.table">
                    <tr>
                      <td>Klocki LEGO Technic</td>
                      <td>99,99 zł\n1 × 99,99 zł</td>
                    </tr>
                    <tr>
                      <td>Baterie AA 4 szt.</td>
                      <td>49,98 zł\n2 × 24,99 zł</td>
                    </tr>
                  </table>
                </body>
                </html>
                """;

        OffsetDateTime emailDate = OffsetDateTime.parse("2026-10-05T14:20:00Z");
        List<ParsedAllegroPurchase> purchases = parser.parseEmailBody("allegroMsg1", emailDate, html);

        assertEquals(2, purchases.size());

        ParsedAllegroPurchase first = purchases.get(0);
        assertEquals("allegroMsg1-1", first.operationId());
        assertEquals(LocalDate.of(2026, 10, 5), first.purchaseDate());
        assertEquals(new BigDecimal("149.97"), first.fullPrice());
        assertEquals("Klocki LEGO Technic", first.itemName());
        assertEquals(new BigDecimal("99.99"), first.itemCost());
        assertEquals("1 × 99,99", first.multipleItems());
        assertEquals("1", first.itemCount());
        assertEquals("99.99", first.itemPrice());

        ParsedAllegroPurchase second = purchases.get(1);
        assertEquals("allegroMsg1-2", second.operationId());
        assertEquals(new BigDecimal("149.97"), second.fullPrice());
        assertEquals("Baterie AA 4 szt.", second.itemName());
        assertEquals(new BigDecimal("49.98"), second.itemCost());
        assertEquals("2", second.itemCount());
        assertEquals("24.99", second.itemPrice());
    }

    @Test
    void shouldParseRealCartWithPaymentEmail() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/allegro/cart-with-payment-2024-02-15.html"), StandardCharsets.UTF_8);
        List<ParsedAllegroPurchase> purchases = parser.parseEmailBody("msgCart1", OffsetDateTime.parse("2024-02-15T18:54:59Z"), html);
        assertEquals(5, purchases.size());
        assertEquals(new BigDecimal("99.92"), purchases.get(0).fullPrice());
        assertEquals("Dobra pamięć, zła pamięć - Ewa Woydyłło (11897281239)", purchases.get(0).itemName());
        assertEquals(new BigDecimal("25.70"), purchases.get(0).itemCost());
        assertEquals(new BigDecimal("19.26"), purchases.get(4).itemCost());
    }
}
