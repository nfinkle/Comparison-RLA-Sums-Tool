import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.VerticalAlignment;
import com.itextpdf.layout.property.TextAlignment;

class Page {
    // ID of the page (which number)
    private final int pageID;
    // index to start at CVR_lines
    private final int votes_line_start_i;
    private final int num_lines_on_page;
    private final int[] partial_sums;
    private final int[] prev_running_sums;
    private final ContestSheets cs; // contest sheet

    public Page(ContestSheets cs, int pageID, int votes_line_start_i, int[] partial_sums, int[] prev_running_sums) {
        this.pageID = pageID;
        this.votes_line_start_i = votes_line_start_i;
        this.cs = cs;
        this.partial_sums = partial_sums;
        this.prev_running_sums = prev_running_sums;
        int lines = cs.ballots() - votes_line_start_i;
        this.num_lines_on_page = lines > cs.BALLOTS_PER_PAGE() ? cs.BALLOTS_PER_PAGE() : lines;
    }

    private Table createPageNumberTable(float left, float bottom, float fontSize) {
        Cell cell = new Cell();
        cell.add(new Paragraph("" + pageID));
        cell.setBorder(new SolidBorder(ColorConstants.BLACK, 1));
        Table table = new Table(1);
        table.addCell(cell);
        table.setPadding(0);
        table.setFixedPosition(pageID, left, bottom, cell.getWidth());
        table.setFontSize(fontSize);
        table.setTextAlignment(TextAlignment.LEFT);
        return table;
    }

    private void addPageNumbers(Document doc, float fontSize, PageSize ps) {
        float top = ps.getHeight() - 2 * fontSize;
        float bottom = 0;
        float left = 0;
        // float right = ps.getWidth() - textWidth * fontSize;
        doc.add(createPageNumberTable(left, top, fontSize));
        doc.add(createPageNumberTable(left, bottom, fontSize));
        // doc.add(createPageNumberTable(pdfdoc, right, top, TextAlignment.RIGHT,
        // fontSize));
        // doc.add(createPageNumberTable(pdfdoc, right, bottom, TextAlignment.RIGHT,
        // fontSize));
    }

    private void addTitle(Document doc, float fontSize, PageSize ps) {
        Table table = new Table(1);
        // table.setWidthPercentage();
        int len = cs.header().length();
        table.setWidth(len);
        float top_row = ps.getHeight() - 2 * fontSize;
        table.setTextAlignment(TextAlignment.CENTER);
        Paragraph p = new Paragraph(cs.header());
        Cell cell = new Cell();
        cell.setBorder(Border.NO_BORDER);
        cell.add(p);
        while (len * fontSize >= ps.getWidth() * 2) {
            fontSize--;
        }
        table.setFontSize(fontSize);
        float middle_with_offset = ps.getWidth() / 2 - (fontSize * len / 2);
        table.setFixedPosition(pageID, middle_with_offset, top_row, len * fontSize);
        table.addCell(cell);
        doc.add(table);
    }

    public void addTitlesToTable(Table table, float bottomBorderThickness) {
        boolean has_parties = false;
        for (int i = 0; i < cs.cols(); i++) {
            if (!cs.party(i).equals("")) {
                has_parties = true;
                break;
            }
        }
        if (has_parties) {
            addPartiesRow(table);
            table.startNewRow();
        }
        addCandidateNamesRow(table, bottomBorderThickness);
    }

    private void addPartiesRow(Table table) {
        table.addCell(new Cell()); // skip the imprintedID cell
        for (int i = 0; i < cs.cols(); i++) {
            Cell c = new Cell().add(new Paragraph(cs.party(i)));
            table.addCell(c);
        }
    }

    private void addCandidateNamesRow(Table table, float bottomBorderThickness) {
        Cell c = new Cell().add(new Paragraph("Ballot ID")); // skip the imprintedID cell
        c.setBorderBottom(new SolidBorder(bottomBorderThickness));
        c.setTextAlignment(TextAlignment.CENTER);
        table.addCell(c);
        for (int i = 0; i < cs.cols(); i++) {
            String candidate = cs.candidate(i);
            candidate = candidate.replaceAll(" ", "\n");
            candidate = candidate.replaceAll("\n/\n", " /\n");
            c = new Cell().add(new Paragraph(candidate));
            c.setBorderBottom(new SolidBorder(bottomBorderThickness));
            table.addCell(c);
        }
    }

    private void addVotesToTable(Table table) {
        for (int i = votes_line_start_i; i < votes_line_start_i + num_lines_on_page; i++) {
            table.startNewRow();
            table.addCell(new Cell().add(new Paragraph(cs.getImprintedID(i))));
            VoteCount vc = cs.getVoteCount(i);
            for (int j = 0; j < this.cs.cols(); j++) {
                String vote = cs.getVote(j, i);
                Cell c = new Cell().add(new Paragraph(vote));
                if (vc == VoteCount.UNDER_VOTE) {
                    c.setBackgroundColor(ColorConstants.LIGHT_GRAY);
                } else if (vc == VoteCount.OVER_VOTE) {
                    c.setBackgroundColor(ColorConstants.RED);
                }
                table.addCell(c);
            }
        }
    }

    private void addSumsToTable(Table table, float topBorderThickness) {

        Cell partial = new Cell().add(new Paragraph("Partial"));
        partial.setBorderTop(new SolidBorder(topBorderThickness));
        table.addCell(partial);
        for (int i = 0; i < partial_sums.length; i++) {
            Paragraph p = new Paragraph(Integer.toString(partial_sums[i]));
            Cell c = new Cell();
            c.setBorderTop(new SolidBorder(topBorderThickness));
            table.addCell(c.add(p));
        }
        table.startNewRow();
        Cell previous = new Cell().add(new Paragraph("Previous"));
        table.addCell(previous);
        for (int i = 0; i < prev_running_sums.length; i++) {
            Paragraph p = new Paragraph(Integer.toString(prev_running_sums[i]));
            table.addCell(new Cell().add(p));
        }
        table.startNewRow();
        Cell running = new Cell().add(new Paragraph("Running"));
        table.addCell(running);
        for (int i = 0; i < prev_running_sums.length; i++) {
            Paragraph p = new Paragraph(Integer.toString(prev_running_sums[i] + partial_sums[i]));
            table.addCell(new Cell().add(p));
        }
    }

    public void addVotesTable(Document doc, PageSize ps, float fontSize) {
        Table table = new Table(cs.cols() + 1);
        addTitlesToTable(table, 2);
        addVotesToTable(table);
        table.setFontSize(fontSize);
        table.startNewRow();
        addSumsToTable(table, 2);
        table.setVerticalAlignment(VerticalAlignment.MIDDLE);
        table.setHorizontalAlignment(HorizontalAlignment.CENTER);
        table.setAutoLayout();
        table.setRelativePosition(0, 50, 0, 0);
        table.setTextAlignment(TextAlignment.CENTER);
        doc.add(table);
    }

    public void formatPDFPage(PdfDocument pdfdoc, Document doc) {
        PageSize ps = pdfdoc.getDefaultPageSize();
        addPageNumbers(doc, 14, ps);
        addTitle(doc, 14, ps);
        addVotesTable(doc, ps, 6);
    }
}