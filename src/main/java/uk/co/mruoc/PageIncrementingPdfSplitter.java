package uk.co.mruoc;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PageRange;
import com.itextpdf.kernel.utils.PdfSplitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.Getter;

public class PageIncrementingPdfSplitter extends PdfSplitter {

    private final AtomicInteger pageNumber;

    @Getter
    private final Collection<String> paths;

    private final String filename;
    private final String folderPath;

    @Builder
    public PageIncrementingPdfSplitter(PdfDocument document, String filename, String folderPath) {
        super(document);
        this.pageNumber = new AtomicInteger(1);
        this.paths = new ArrayList<>();
        this.filename = filename;
        this.folderPath = folderPath;
    }

    @Override
    protected PdfWriter getNextPdfWriter(PageRange documentPageRange) {
        try {
            var path = String.format("%s/pages/page-%d-%s", folderPath, pageNumber.getAndIncrement(), filename);
            paths.add(path);
            return new PdfWriter(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
