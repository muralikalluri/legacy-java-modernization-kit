package com.harborclaims.portal.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.harborclaims.portal.claims.ClaimManager;
import com.lowagie.text.pdf.PdfReader;

public class PdfReportBuilderTest {

    private static List<ClaimManager.StatusRow> rows() {
        List<Object[]> raw = new ArrayList<Object[]>();
        raw.add(new Object[] {"NEW", Integer.valueOf(2), new BigDecimal("1500.50")});
        raw.add(new Object[] {"PAID", Integer.valueOf(1), new BigDecimal("12000.00")});
        return ClaimManager.buildStatusRows(raw);
    }

    /** iText 2 writes pdf date and id entries, so compare the text of the page, never the bytes. */
    private static String pageText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        String content = new String(reader.getPageContent(1), "ISO-8859-1");
        Matcher m = Pattern.compile("\\(((?:\\\\.|[^\\\\)])*)\\)").matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append(m.group(1)).append('|');
        }
        return sb.toString();
    }

    @Test
    public void producesAPdfWithTheReportText() throws Exception {
        byte[] pdf = PdfReportBuilder.claimsByStatus(rows());
        assertEquals("%PDF", new String(pdf, 0, 4, "ISO-8859-1"));
        String text = pageText(pdf);
        assertTrue(text, text.contains("Harbor Claims Portal - Claims by status"));
        assertTrue(text, text.contains("Under review|"));
        assertTrue(text, text.contains("1,500.50|"));
        assertTrue(text, text.contains("13,500.50|"));
        assertTrue(text, text.contains("66.7%|"));
    }

    @Test
    public void everyStatusAppearsEvenWithZeroClaims() throws Exception {
        String text = pageText(PdfReportBuilder.claimsByStatus(rows()));
        for (String label : new String[] {"New", "Under review", "Approved", "Denied", "Paid", "Reopened"}) {
            assertTrue(label, text.contains(label + "|"));
        }
    }

    @Test
    public void sameDataGivesTheSameText() throws Exception {
        assertEquals(pageText(PdfReportBuilder.claimsByStatus(rows())), pageText(PdfReportBuilder.claimsByStatus(rows())));
    }
}
