package top.productivitytools.spendings.webapi.services;

import org.junit.jupiter.api.Test;
import top.productivitytools.spendings.webapi.dto.ParsedAccountBalance;
import top.productivitytools.spendings.webapi.dto.ParsedSpending;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MBankEmailParserTest {

    private final MBankEmailParser parser = new MBankEmailParser();

    @Test
    void shouldParseHtmlAttachmentWithAllOperationTypes() {
        String html = """
                <html>
                <body>
                <table>
                  <tbody>
                    <tr>
                      <td>
                        <h1>Powiadomienie e-mail - 2026-10-05</h1>
                        <table>
                          <tbody>
                            <tr>
                              <td>Dane adresata:</td>
                              <td>PAWE GRZEGORZ WUJCZYK PWUJCZYK@GMAIL.COM</td>
                            </tr>
                            <tr>
                              <td>Czas</td>
                              <td>Szczegóły</td>
                            </tr>
                            <tr>
                              <td>10:15:00</td>
                              <td>mBank: Autoryzacja karty *1234: BIEDRONKA. Kwota: 45,60 PLN. Dost.: 1200,00 PLN.</td>
                            </tr>
                            <tr>
                              <td>11:20:00</td>
                              <td>mBank: Przelew wych. z rach. *5678 na rach. *9999 kwota 150,00 PLN dla Jan Kowalski Dost. 1050,00 PLN.</td>
                            </tr>
                            <tr>
                              <td>12:30:00</td>
                              <td>mBank: Przelew przych. z rach. *1111 na rach. *5678 kwota 500,50 PLN od Pracodawca Sp. z o.o. Dost. 1550,50 PLN.</td>
                            </tr>
                            <tr>
                              <td>13:45:00</td>
                              <td>mBank: Obciazenie rach. *5678 na kwote 29,99 PLN tytulem: Oplata za konto; saldo: 1520,51 PLN</td>
                            </tr>
                            <tr>
                              <td>14:00:00</td>
                              <td>mBank: Uznanie na rach. *5678 na kwote 100,00 PLN tytulem: Zwrot srodkow; saldo: 1620,51 PLN</td>
                            </tr>
                            <tr>
                              <td>15:00:00</td>
                              <td>mBank: Niepoprawne logowanie do serwisu</td>
                            </tr>
                          </tbody>
                        </table>
                      </td>
                    </tr>
                  </tbody>
                </table>
                </body>
                </html>
                """;

        List<ParsedSpending> spendings = parser.parseHtmlAttachment("msg123", html);

        assertEquals(5, spendings.size());

        ParsedSpending card = spendings.get(0);
        assertEquals("msg123-2", card.operationId());
        assertEquals("2026-10-05", card.operationDate());
        assertEquals("10:15:00", card.operationTime());
        assertEquals("Autoryzacja karty", card.operationType());
        assertEquals("*1234", card.srcAccount());
        assertEquals("", card.dstAccount());
        assertEquals(new BigDecimal("-45.60"), card.amount());
        assertEquals("PLN", card.currency());
        assertEquals("BIEDRONKA", card.name());
        assertEquals("1200,00", card.amountLeft());
        assertEquals("PLN", card.amountLeftCurrency());

        ParsedSpending outgoing = spendings.get(1);
        assertEquals("msg123-3", outgoing.operationId());
        assertEquals("Przelew wychodzacy", outgoing.operationType());
        assertEquals("*5678", outgoing.srcAccount());
        assertEquals("*9999", outgoing.dstAccount());
        assertEquals(new BigDecimal("-150.00"), outgoing.amount());
        assertEquals("PLN", outgoing.currency());
        assertEquals("Jan Kowalski", outgoing.name());
        assertEquals("1050,00", outgoing.amountLeft());
        assertEquals("PLN", outgoing.amountLeftCurrency());

        ParsedSpending incoming = spendings.get(2);
        assertEquals("msg123-4", incoming.operationId());
        assertEquals("Przelew przychodzący", incoming.operationType());
        assertEquals("*1111", incoming.srcAccount());
        assertEquals("*5678", incoming.dstAccount());
        assertEquals(new BigDecimal("500.50"), incoming.amount());
        assertEquals("PLN", incoming.currency());
        assertEquals("Pracodawca Sp. z o.o.", incoming.name());
        assertEquals("1550,50", incoming.amountLeft());
        assertEquals("PLN", incoming.amountLeftCurrency());

        ParsedSpending debit = spendings.get(3);
        assertEquals("msg123-5", debit.operationId());
        assertEquals("Obciazenie", debit.operationType());
        assertEquals("*5678", debit.srcAccount());
        assertEquals("", debit.dstAccount());
        assertEquals(new BigDecimal("-29.99"), debit.amount());
        assertEquals("PLN", debit.currency());
        assertEquals("Oplata za konto", debit.name());
        assertEquals("1520,51", debit.amountLeft());
        assertEquals("PLN", debit.amountLeftCurrency());

        ParsedSpending credit = spendings.get(4);
        assertEquals("msg123-6", credit.operationId());
        assertEquals("Uznanie", credit.operationType());
        assertEquals("", credit.srcAccount());
        assertEquals("*5678", credit.dstAccount());
        assertEquals(new BigDecimal("100.00"), credit.amount());
        assertEquals("PLN", credit.currency());
        assertEquals("Zwrot srodkow", credit.name());
        assertEquals("1620,51", credit.amountLeft());
        assertEquals("PLN", credit.amountLeftCurrency());
    }

    @Test
    void shouldParseBalanceRowsAndSkipThemInSpendings() {
        String html = """
                <html><body><table><tbody><tr><td>
                  <h1>Powiadomienie e-mail - 2026-09-02</h1>
                  <table><tbody>
                    <tr><td>Czas operacji (GG:MM)</td><td>Opis operacji</td></tr>
                    <tr><td>05:40</td><td>mBank: Saldo rach. 42109862 w dniu 2026-08-31. Dostepne 6491,93 PLN</td></tr>
                    <tr><td>05:40</td><td>mBank: Saldo rach. 85246207 w dniu 2026-09-02. Dostepne 0,00 PLN</td></tr>
                    <tr><td>05:40</td><td>mBank: Saldo rach. 00561357 w dniu 2026-08-31. Dostepne 1026,85 EUR</td></tr>
                    <tr><td>05:40</td><td>mBank: Saldo rach. 00561332 w dniu 2026-09-02. Dostepne 0,00 USD</td></tr>
                    <tr><td>06:10</td><td>mBank: Przelew wych. z rach. 85270738 na rach. 7116...796001 kwota 2640,00 PLN dla SZKOA PODSTAWOWA NR; CZESNE MAGDA...; Dost. 3060,00 PLN</td></tr>
                    <tr><td>07:52</td><td>mBank: Odmowa autoryzacji 4838***3703: BRAK RODKW. AWS EMEA aws.amazon.co. Naleznosc: 1,23 USD. Dostepne: 0,00 USD.</td></tr>
                  </tbody></table>
                </td></tr></tbody></table></body></html>
                """;

        List<ParsedAccountBalance> balances = parser.parseAccountBalances("msg9", html);
        assertEquals(4, balances.size());

        ParsedAccountBalance first = balances.get(0);
        assertEquals("msg9-1", first.operationId());
        assertEquals("42109862", first.account());
        assertEquals(LocalDate.of(2026, 8, 31), first.balanceDate());
        assertEquals("05:40", first.operationTime());
        assertEquals(new BigDecimal("6491.93"), first.amount());
        assertEquals("PLN", first.currency());

        assertEquals(new BigDecimal("0.00"), balances.get(1).amount());
        assertEquals("85246207", balances.get(1).account());
        assertEquals(LocalDate.of(2026, 9, 2), balances.get(1).balanceDate());

        assertEquals("EUR", balances.get(2).currency());
        assertEquals(new BigDecimal("1026.85"), balances.get(2).amount());

        assertEquals("USD", balances.get(3).currency());
        assertEquals("00561332", balances.get(3).account());

        // Balance rows must not become spendings and must not break parsing of the real transfer
        List<ParsedSpending> spendings = parser.parseHtmlAttachment("msg9", html);
        assertEquals(1, spendings.size());
        assertEquals("Przelew wychodzacy", spendings.get(0).operationType());
        assertEquals(new BigDecimal("-2640.00"), spendings.get(0).amount());
        assertEquals("85270738", spendings.get(0).srcAccount());
    }

    @Test
    void shouldReturnNullForNonBalanceRow() {
        assertNull(parser.parseBalanceRow("x-1", "10:00", "mBank: Autoryzacja karty *1234: SKLEP. Kwota: 1,00 PLN. Dost.: 10,00 PLN."));
        assertNull(parser.parseTransferRow("x-1", "2026-09-02", "05:40",
                "mBank: Saldo rach. 42109862 w dniu 2026-08-31. Dostepne 6491,93 PLN"));
    }

    @Test
    void shouldRejectMalformedBalanceRow() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseBalanceRow("x-1", "05:40", "mBank: Saldo rach. 42109862 cos dziwnego"));
    }

    @Test
    void shouldParseRealDailyNotificationWithMangledPolishCharacters() throws IOException {
        String html;
        try (InputStream in = getClass().getResourceAsStream("/mbank/notification-2026-09-02.html")) {
            assertNotNull(in, "fixture missing");
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<ParsedAccountBalance> balances = parser.parseAccountBalances("real", html);
        assertEquals(13, balances.size());
        assertEquals("42109862", balances.get(0).account());
        assertEquals(LocalDate.of(2026, 8, 31), balances.get(0).balanceDate());
        assertEquals(new BigDecimal("6491.93"), balances.get(0).amount());
        assertEquals("PLN", balances.get(0).currency());
        assertEquals(new BigDecimal("10000.00"), balances.get(2).amount());
        assertEquals("EUR", balances.get(11).currency());
        assertEquals(new BigDecimal("235.87"), balances.get(11).amount());

        List<ParsedSpending> spendings = parser.parseHtmlAttachment("real", html);
        // 2 outgoing + 2 incoming + 4 card + 6 debits = 14 (balances and "Odmowa autoryzacji" skipped)
        assertEquals(14, spendings.size());
        assertTrue(spendings.stream().allMatch(s -> "2026-09-02".equals(s.operationDate())));

        ParsedSpending outgoing = spendings.get(0);
        assertEquals("Przelew wychodzacy", outgoing.operationType());
        assertEquals("06:10", outgoing.operationTime());
        assertEquals("85270738", outgoing.srcAccount());
        assertEquals("7116...796001", outgoing.dstAccount());
        assertEquals(new BigDecimal("-2640.00"), outgoing.amount());
        assertEquals("SZKOA PODSTAWOWA NR; CZESNE MAGDA...;", outgoing.name());
        assertEquals("3060,00", outgoing.amountLeft());

        ParsedSpending incoming = spendings.get(1);
        assertEquals("Przelew przychodzący", incoming.operationType());
        assertEquals("42109862", incoming.dstAccount());
        assertEquals(new BigDecimal("4000.00"), incoming.amount());
        assertEquals("20243,70", incoming.amountLeft());

        ParsedSpending card = spendings.get(3);
        assertEquals("Autoryzacja karty", card.operationType());
        assertEquals("5575***5205", card.srcAccount());
        assertEquals("Allegro Poznan", card.name());
        assertEquals(new BigDecimal("-59.99"), card.amount());
        assertEquals("20183,71", card.amountLeft());

        ParsedSpending debit = spendings.get(13);
        assertEquals("Obciazenie", debit.operationType());
        assertEquals("42109862", debit.srcAccount());
        assertEquals(new BigDecimal("-990.70"), debit.amount());
        assertEquals("ZARA.COM", debit.name());
        assertEquals("13953,44", debit.amountLeft());
    }
}
