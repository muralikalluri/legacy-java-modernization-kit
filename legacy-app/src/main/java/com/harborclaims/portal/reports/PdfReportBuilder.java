package com.harborclaims.portal.reports;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.harborclaims.portal.claims.ClaimManager;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Builds the claims-by-status PDF with iText 2. Base-14 Helvetica only (not embedded) and no generated-at
 * time in the content, so the extracted text is the same for the same data.
 */
public final class PdfReportBuilder {

    private PdfReportBuilder() {
    }

    public static byte[] claimsByStatus(List<ClaimManager.StatusRow> rows) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);

        doc.add(new Paragraph("Harbor Claims Portal - Claims by status", title));
        doc.add(new Paragraph("All figures are fictional demo data.", body));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {3f, 1.5f, 3f, 1.5f});
        header(table, "Status", head);
        header(table, "Claims", head);
        header(table, "Total claimed", head);
        header(table, "Share", head);
        for (ClaimManager.StatusRow r : rows) {
            cell(table, r.getLabel(), body, Element.ALIGN_LEFT);
            cell(table, String.valueOf(r.getCount()), body, Element.ALIGN_RIGHT);
            cell(table, ClaimManager.moneyText(r.getTotal()), body, Element.ALIGN_RIGHT);
            cell(table, r.getShare(), body, Element.ALIGN_RIGHT);
        }
        cell(table, "All", head, Element.ALIGN_LEFT);
        cell(table, String.valueOf(ClaimManager.grandCount(rows)), head, Element.ALIGN_RIGHT);
        cell(table, ClaimManager.moneyText(ClaimManager.grandTotal(rows)), head, Element.ALIGN_RIGHT);
        cell(table, "", head, Element.ALIGN_RIGHT);
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    private static void header(PdfPTable table, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        table.addCell(c);
    }

    private static void cell(PdfPTable table, String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        table.addCell(c);
    }
}
