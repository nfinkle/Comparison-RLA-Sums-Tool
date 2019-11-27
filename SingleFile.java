import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;

import java.io.FileNotFoundException;
import java.util.ArrayList;

class SingleFile {

    public static void updatePrevRunningSums(ArrayList<ArrayList<Integer>> prev_running_sums,
            ArrayList<ArrayList<Integer>> partial_sums) {
        for (int c = 0; c < partial_sums.size(); c++) {
            for (int i = 0; i < partial_sums.get(c).size(); i++) {
                int element = prev_running_sums.get(c).get(i) + partial_sums.get(c).get(i);
                prev_running_sums.get(c).set(i, element);
            }
        }
    }

    public static void writePDF(Contest[] contests, VotingSheets vs, PdfDocument pdfdoc) throws FileNotFoundException {
        Document doc = new Document(pdfdoc);
        int BALLOTS_PER_PAGE = vs.BALLOTS_PER_PAGE();
        int num_pages = (vs.ballots() + BALLOTS_PER_PAGE - 1) / BALLOTS_PER_PAGE; // Round up
        doc.setMargins(0, 0, 0, 0);
        ArrayList<ArrayList<Integer>> prev_running_sums = new ArrayList<ArrayList<Integer>>();
        for (int j = 0; j < contests.length; j++) {
            prev_running_sums.add(new ArrayList<Integer>(contests[j].cols()));
            for (int i = 0; i < contests[j].cols(); i++) {
                prev_running_sums.get(j).add(0);
            }
        }
        for (int i = 1, votes_start_i = 0; i <= num_pages; i++, votes_start_i += BALLOTS_PER_PAGE) {
            pdfdoc.addNewPage();
            ArrayList<ArrayList<Integer>> partial_sums = new ArrayList<ArrayList<Integer>>(contests.length);
            for (int j = 0; j < contests.length; j++) {
                partial_sums.add(contests[j].buildPartialSums(votes_start_i));
            }

            Page p = new Page(contests, i, votes_start_i, partial_sums, prev_running_sums);
            p.formatPDFPage(pdfdoc.getDefaultPageSize(), doc);
            updatePrevRunningSums(prev_running_sums, partial_sums);
            if (i != num_pages)
                doc.add(new AreaBreak());
        }
        pdfdoc.removePage(num_pages + 1);
        doc.close();
    }
}