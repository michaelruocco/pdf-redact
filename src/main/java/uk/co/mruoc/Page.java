package uk.co.mruoc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;

@RequiredArgsConstructor
@Getter
@Slf4j
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
        return blocks.stream()
                .filter(new BlockPredicate(text))

                .toList();
    }

    @RequiredArgsConstructor
    private static class BlockPredicate implements Predicate<Block> {

        private final String text;

        @Override
        public boolean test(Block block) {
            if (block.blockType() == BlockType.WORD) {
                var words = Arrays.asList(text.split("\\s+"));
                return words.contains(block.text());
            }
            if (block.blockType() == BlockType.LINE) {
                return block.text().contains(text);
            }
            return false;
        }
    }

    public void debug() {
        log.info(String.format("page %d", number));
        log.debug(path);
        log.debug("blocks");
        blocks.forEach(block -> log.debug(String.format("%s %s", block.blockType(), block.text())));
        log.debug("entities");
        entities.forEach(entity -> log.info(String.format("%s %s", entity.getText(), entity.getType())));
    }
}
