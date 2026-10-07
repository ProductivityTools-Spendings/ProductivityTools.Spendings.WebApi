package top.productivitytools.spendings.webapi.services;

import org.junit.jupiter.api.Test;
import top.productivitytools.spendings.webapi.dto.ParsedExpense;

import java.math.BigDecimal;
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

        List<ParsedExpense> expenses = parser.parseHtmlAttachment("msg123", html);

        assertEquals(5, expenses.size());

        ParsedExpense card = expenses.get(0);
        assertEquals("msg123-1", card.operationId());
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

        ParsedExpense outgoing = expenses.get(1);
        assertEquals("msg123-2", outgoing.operationId());
        assertEquals("Przelew wychodzacy", outgoing.operationType());
        assertEquals("*5678", outgoing.srcAccount());
        assertEquals("*9999", outgoing.dstAccount());
        assertEquals(new BigDecimal("-150.00"), outgoing.amount());
        assertEquals("PLN", outgoing.currency());
        assertEquals("Jan Kowalski", outgoing.name());
        assertEquals("1050,00", outgoing.amountLeft());
        assertEquals("PLN", outgoing.amountLeftCurrency());

        ParsedExpense incoming = expenses.get(2);
        assertEquals("msg123-3", incoming.operationId());
        assertEquals("Przelew przychodzący", incoming.operationType());
        assertEquals("*1111", incoming.srcAccount());
        assertEquals("*5678", incoming.dstAccount());
        assertEquals(new BigDecimal("500.50"), incoming.amount());
        assertEquals("PLN", incoming.currency());
        assertEquals("Pracodawca Sp. z o.o.", incoming.name());
        assertEquals("1550,50", incoming.amountLeft());
        assertEquals("PLN", incoming.amountLeftCurrency());

        ParsedExpense debit = expenses.get(3);
        assertEquals("msg123-4", debit.operationId());
        assertEquals("Obciazenie", debit.operationType());
        assertEquals("*5678", debit.srcAccount());
        assertEquals("", debit.dstAccount());
        assertEquals(new BigDecimal("-29.99"), debit.amount());
        assertEquals("PLN", debit.currency());
        assertEquals("Oplata za konto", debit.name());
        assertEquals("1520,51", debit.amountLeft());
        assertEquals("PLN", debit.amountLeftCurrency());

        ParsedExpense credit = expenses.get(4);
        assertEquals("msg123-5", credit.operationId());
        assertEquals("Uznanie", credit.operationType());
        assertEquals("", credit.srcAccount());
        assertEquals("*5678", credit.dstAccount());
        assertEquals(new BigDecimal("100.00"), credit.amount());
        assertEquals("PLN", credit.currency());
        assertEquals("Zwrot srodkow", credit.name());
        assertEquals("1620,51", credit.amountLeft());
        assertEquals("PLN", credit.amountLeftCurrency());
    }
}
