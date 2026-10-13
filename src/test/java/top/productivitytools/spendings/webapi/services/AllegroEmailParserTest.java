package top.productivitytools.spendings.webapi.services;

import org.junit.jupiter.api.Test;
import top.productivitytools.spendings.webapi.dto.ParsedAllegroPurchase;
import top.productivitytools.spendings.webapi.dto.ParsedAllegroRefund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldRecogniseRefundSubject() {
        assertTrue(parser.isRefundSubject("Zwrot 64,58 zł za zakup 2 przedmiotów w tym: Buty do wody"));
        assertFalse(parser.isRefundSubject("Kupiłeś i zapłaciłeś: Buty do wody"));
        assertFalse(parser.isRefundSubject("Zapłaciłeś za zamówienie"));
        assertFalse(parser.isRefundSubject(null));
    }

    @Test
    void shouldParsePartialRefundFromSubjectAndBody() {
        String subject = "Zwrot 64,58 zł za zakup 2 przedmiotów w tym: Buty do wody dziecięce AQUASTIC Kea niebieski 32";
        String html = """
                <html><body>
                  <p>Cześć Paweł,</p>
                  <p>Sprzedający <a href="#">sklep_sportano</a> zlecił częściowy zwrot za Twoje zamówienie. Pieniądze są już w drodze do Ciebie!</p>
                  <table>
                    <tr><td>Podsumowanie zwrotu</td></tr>
                    <tr><td>Kwota do zwrotu:</td><td><b>64,58 zł</b> (z 104,57 zł)</td></tr>
                    <tr><td>Gdzie trafią pieniądze:</td><td>na konto bankowe (lub kartę), z którego opłacono zakup.</td></tr>
                  </table>
                </body></html>
                """;

        ParsedAllegroRefund refund = parser.parseRefund("msgR1", OffsetDateTime.parse("2026-09-14T09:31:00Z"), subject, html);

        assertEquals("msgR1-refund", refund.operationId());
        assertEquals(LocalDate.of(2026, 9, 14), refund.refundDate());
        assertEquals(new BigDecimal("64.58"), refund.amount());
        assertEquals(new BigDecimal("104.57"), refund.originalAmount());
        assertTrue(refund.partial());
        assertEquals("sklep_sportano", refund.seller());
        assertEquals("2 przedmiotów w tym: Buty do wody dziecięce AQUASTIC Kea niebieski 32", refund.description());
    }

    @Test
    void shouldParseFullRefundWithThousandsSeparatorAndWithoutBodyDetails() {
        ParsedAllegroRefund refund = parser.parseRefund("msgR2", OffsetDateTime.parse("2026-09-14T09:31:00Z"),
                "Zwrot 1 204,50 zł za zakup Rower górski", "<html><body>Pieniądze są w drodze.</body></html>");

        assertEquals(new BigDecimal("1204.50"), refund.amount());
        assertNull(refund.originalAmount());
        assertFalse(refund.partial());
        assertNull(refund.seller());
        assertEquals("Rower górski", refund.description());
    }

    @Test
    void shouldFallBackToBodyAmountWhenSubjectHasNone() {
        ParsedAllegroRefund refund = parser.parseRefund("msgR3", null, "Zwrot za zamówienie",
                "<html><body>Kwota do zwrotu: 12,00 zł</body></html>");
        assertEquals(new BigDecimal("12.00"), refund.amount());
    }
}
