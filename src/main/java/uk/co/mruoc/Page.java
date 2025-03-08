package uk.co.mruoc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;

@RequiredArgsConstructor
@Getter
public class Page {

    private final int number;
    private final String path;

    @With
    private final Collection<Block> blocks;

    @With
    private final Collection<PiiEntityText> entities;

    public Page(int number, String path) {
        this(number, path, Collections.emptyList(), Collections.emptyList());
    }

    public String asText() {
        return blocks.stream()
                .filter(b -> b.blockType() == BlockType.LINE)
                .map(Block::text)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public Collection<Block> findWordBlocksByText(String text) {
        var words = Arrays.asList(text.split(" "));
        var found = blocks.stream()
                .filter(b -> b.blockType() == BlockType.WORD)
                .filter(b -> words.contains(b.text()))
                .toList();
        System.out.println(text + " found blocks "
                + found.stream().map(b -> b.text() + " " + b.blockType()).toList());
        return found;
    }
}
